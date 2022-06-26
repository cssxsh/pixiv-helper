package xyz.cssxsh.mirai.pixiv

import io.ktor.client.statement.*
import io.ktor.util.*
import kotlinx.coroutines.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.auth.*
import xyz.cssxsh.pixiv.exception.*
import java.io.IOException
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.properties.*
import kotlin.reflect.*

/**
 * 客户端池，key 是 pixiv uid
 */
public object PixivClientPool : ReadOnlyProperty<PixivHelper, PixivAuthClient>, CoroutineScope {
    private val logger by lazy { MiraiLogger.Factory.create(this::class, identity = "pixiv-client-pool") }

    override val coroutineContext: CoroutineContext =
        CoroutineName(name = "pixiv-client-pool") + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
            logger.warning({ "$throwable in $context" }, throwable)
        }

    internal val clients: MutableMap<Long, AuthClient> = ConcurrentHashMap()

    internal val binded: MutableMap<Long, Long> get() = PixivAuthData.binded

    private val authed: Set<Long> get() = PixivAuthData.results.keys

    internal val default: Long get() = PixivAuthData.default

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): PixivAuthClient {
        return get(id = thisRef.id) ?: free()
    }

    public fun free(): PixivAuthClient {
        val uid = authed.randomOrNull() ?: throw IllegalStateException("账号池为空，请登录 Pixiv 账号")
        return FreeClient(delegate = user(uid = uid))
    }

    public fun console(): AuthClient? = clients[default]

    public fun user(uid: Long): AuthClient {
        return clients.getOrPut(key = uid) {
            check(uid in authed) { "$uid 此账户未登录" }
            AuthClient(uid = uid)
        }
    }

    public operator fun get(id: Long): AuthClient? {
        return user(uid = binded[id] ?: return null)
    }

    public fun bind(uid: Long, subject: Long) {
        binded[subject] = uid
    }

    public suspend fun auth(block: suspend (PixivAuthClient) -> Unit) {
        val client = TempClient()
        block.invoke(client)
        val auth = client.auth
        if (auth != null) {
            PixivAuthData += auth
            clients[auth.user.uid] = AuthClient(uid = auth.user.uid)
        }
    }

    private val handle: suspend PixivAuthClient.(Throwable) -> Boolean = { throwable ->
        when (throwable) {
            is IOException -> {
                logger.warning { "Pixiv Api 错误, 已忽略: $throwable" }
                true
            }
            is AppApiException -> {
                val url = throwable.response.request.url
                val request = throwable.response.request.headers.toMap()
                val response = throwable.response.headers.toMap()
                when {
                    "Please check your Access Token to fix this." in throwable.message -> {
                        logger.warning { "PIXIV API OAuth 错误, 将刷新 Token $url with $request" }
                        try {
                            refresh()
                        } catch (cause: Throwable) {
                            logger.warning { "刷新 Token 失败 $cause" }
                        }
                        true
                    }
                    "Rate Limit" in throwable.message -> {
                        logger.warning { "PIXIV API限流, 将延时: ${PIXIV_RATE_LIMIT_DELAY}ms $url with $response" }
                        delay(PIXIV_RATE_LIMIT_DELAY)
                        true
                    }
                    else -> false
                }
            }
            else -> false
        }
    }

    public class TempClient : PixivAuthClient() {
        override val config: PixivConfig = DEFAULT_PIXIV_CONFIG.copy()

        public override var auth: AuthResult? = null

        override val coroutineContext: CoroutineContext = PixivClientPool.childScopeContext(name = "temp-client")

        override val ignore: suspend (Throwable) -> Boolean get() = { handle(it) }
    }

    public class AuthClient(public val uid: Long) : PixivAuthClient() {

        override val config: PixivConfig = DEFAULT_PIXIV_CONFIG.copy()

        public override var auth: AuthResult? by PixivAuthData

        override val coroutineContext: CoroutineContext = PixivClientPool.childScopeContext(name = "auth-client-$uid")

        override val ignore: suspend (Throwable) -> Boolean = { handle(it) }
    }

    public class FreeClient(private val delegate: AuthClient) : PixivAuthClient() {

        override val config: PixivConfig = DEFAULT_PIXIV_CONFIG.copy()

        override var auth: AuthResult? by delegate::auth

        override val coroutineContext: CoroutineContext = PixivClientPool.childScopeContext(name = "free-client")

        override val ignore: suspend (Throwable) -> Boolean = { handle(it) }
    }
}