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

    private val clients: MutableMap<Long, PixivAuthClient> = ConcurrentHashMap()

    private val binded: MutableMap<Long, Long> = ConcurrentHashMap()

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): PixivAuthClient {
        return client(uid = binded[thisRef.id] ?: return free())
    }

    public fun free(): PixivAuthClient = TODO()

    public fun client(uid: Long): PixivAuthClient {
        return clients.getOrPut(key = uid) { AuthClient(uid = uid) }
    }

    public fun bind(uid: Long, subject: Long) {
        binded[uid] = subject
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

    // Free, Temp, Save

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
        override val config: PixivConfig = DEFAULT_PIXIV_CONFIG.copy(proxy = ProxyApi)

        override val coroutineContext: CoroutineContext = childScopeContext(name = "temp-client")

        override val ignore: suspend (Throwable) -> Boolean get() = { handle(it) }

        public override var auth: AuthResult? = null
    }

    public class AuthClient(public val uid: Long) : PixivAuthClient() {

        override val config: PixivConfig = DEFAULT_PIXIV_CONFIG.copy(proxy = ProxyApi)

        override var auth: AuthResult? by PixivAuthData

        override val coroutineContext: CoroutineContext = childScopeContext(name = "auth-client-$uid")

        override val ignore: suspend (Throwable) -> Boolean = { handle(it) }
    }
}