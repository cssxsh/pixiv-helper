@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.*
import okhttp3.internal.http2.StreamResetException
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.client.*
import xyz.cssxsh.pixiv.data.AuthInfoDelegate
import xyz.cssxsh.pixiv.data.AuthResult
import xyz.cssxsh.pixiv.data.ConfigDelegate
import xyz.cssxsh.pixiv.data.ExpiresTimeDelegate
import xyz.cssxsh.pixiv.data.apps.IllustInfo
import java.io.EOFException
import java.net.ConnectException
import java.net.UnknownHostException
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import javax.net.ssl.SSLException
import kotlin.time.minutes

/**
 * 助手实例
 */
class PixivHelper(val contact: Contact) : SimplePixivClient(
    parentCoroutineContext = PixivHelperPlugin.coroutineContext,
    coroutineName = "PixivHelper:${contact}",
    config = PixivConfig()
) {

    override var config: PixivConfig by ConfigDelegate(contact)

    override var authInfo: AuthResult.AuthInfo? by AuthInfoDelegate(contact)

    public override var expiresTime: OffsetDateTime by ExpiresTimeDelegate(contact)

    override val apiIgnore: suspend (Throwable) -> Boolean = { throwable ->
        when (throwable) {
            is SSLException,
            is EOFException,
            is ConnectException,
            is SocketTimeoutException,
            is HttpRequestTimeoutException,
            is StreamResetException,
            is UnknownHostException,
            -> {
                logger.warning { "PIXIV API错误, 已忽略: ${throwable.message}" }
                true
            }
            else -> when (throwable.message) {
                "Required SETTINGS preface not received" -> {
                    logger.warning { "API错误, 已忽略: ${throwable.message}" }
                    true
                }
                "Rate Limit" -> {
                    logger.warning { "API限流, 已延时: ${throwable.message}" }
                    delay((10).minutes)
                    true
                }
                else -> false
            }
        }
    }

    val historyQueue by lazy {
        ArrayBlockingQueue<Long>(PixivHelperSettings.minInterval)
    }

    var minSanityLevel = 1
        set(value) {
            field = minOf(value, 6)
        }

    var minBookmarks: Long = 0

    var simpleInfo: Boolean
        get() = PixivConfigData.simpleInfo.getOrPut(contact.toString()) { contact !is Friend }
        set(value) {
            PixivConfigData.simpleInfo[contact.toString()] = value
        }

    private var cacheJob: Job? = null

    private val cacheList: MutableList<Pair<String, suspend PixivHelper.() -> List<IllustInfo>>> = mutableListOf()

    private suspend fun Collection<IllustInfo>.loadCache(name: String): Unit = sortedBy { it.pid }.run {
        logger.verbose { "任务<$name>共${size}个作品信息将会被尝试缓存" }
        runCatching {
            reply("任务<$name>有{${first().pid..last().pid}}共${size}个新作品等待缓存")
        }
        runCatching {
            size to map { illust ->
                async {
                    illust to (isActive && illust.runCatching {
                        getImages()
                    }.onFailure {
                        logger.warning({ "任务<$name>获取作品(${illust.pid})[${illust.title}]错误" }, it)
                        runCatching {
                            reply("任务<$name>获取作品(${illust.pid})[${illust.title}]错误, ${it.message}")
                        }
                    }.isSuccess)
                }
            }.awaitAll().mapNotNull { (illust, success) ->
                illust.takeIf {
                    success
                }
            }.apply { saveToSQLite() }.size
        }.onSuccess { (total, success) ->
            logger.verbose { "任务<$name>缓存完毕, 共${total}个新作品, 缓存成功${success}个" }
            runCatching {
                reply("任务<$name>缓存完毕, 共${total}个新作品, 缓存成功${success}个")
            }
        }.onFailure {
            logger.warning({ "任务<$name>缓存失败" }, it)
            runCatching {
                reply("任务<$name>缓存失败, ${it.message}")
            }
        }
    }

    fun addCacheJob(
        name: String,
        write: Boolean = true,
        block: suspend PixivHelper.() -> List<IllustInfo>,
    ): Boolean = cacheList.add(name to block).also {
        logger.verbose { "任务<$name>已添加" }
        if (cacheJob?.takeIf { it.isActive } == null) {
            cacheJob = launch(Dispatchers.IO) {
                while (isActive && cacheList.isNotEmpty()) {
                    cacheList.removeFirst().let { (name, getIllusts) ->
                        getIllusts.invoke(this@PixivHelper).let { list ->
                            if (write) list.writeToCache()
                            useArtWorkInfoMapper { mapper ->
                                list.groupBy {
                                    mapper.contains(it.pid)
                                }
                            }.let { map ->
                                map[true]?.updateToSQLite()
                                map[false]?.loadCache(name)
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun cacheStop() =
        cacheJob?.apply {
            cacheList.clear()
            cancelAndJoin()
        }

    var followJob: Job? = null

    override fun config(block: PixivConfig.() -> Unit) =
        config.apply(block).also { config = it }

    override suspend fun refresh(token: String) = super.refresh(token).also {
        logger.info { "$it by RefreshToken: $token, ExpiresTime: $expiresTime" }
    }

    override suspend fun login(mailOrPixivID: String, password: String) = super.login(mailOrPixivID, password).also {
        logger.info { "$it by Account: $mailOrPixivID, ExpiresTime: $expiresTime" }
    }

    /**
     * 给这个助手的联系人发送消息
     */
    @JvmSynthetic
    suspend fun reply(message: Message): MessageReceipt<Contact> =
        contact.sendMessage(message)

    /**
     * 给这个助手的联系人发送文本消息
     */
    @JvmSynthetic
    suspend fun reply(plain: String): MessageReceipt<Contact> =
        contact.sendMessage(PlainText(plain))
}