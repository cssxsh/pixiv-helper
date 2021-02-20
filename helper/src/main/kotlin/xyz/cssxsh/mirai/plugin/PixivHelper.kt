@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import okhttp3.internal.http2.StreamResetException
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.client.*
import xyz.cssxsh.pixiv.data.*
import xyz.cssxsh.pixiv.data.apps.*
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import java.time.OffsetDateTime
import javax.net.ssl.SSLException
import kotlin.time.*

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
            is SocketException,
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
                    (3).minutes.let {
                        logger.warning { "API限流, 已延时: $it" }
                        delay(it)
                    }
                    true
                }
                else -> false
            }
        }
    }

    var simpleInfo: Boolean
        get() = PixivConfigData.simpleInfo.getOrPut(contact.toString()) { contact !is Friend }
        set(value) {
            PixivConfigData.simpleInfo[contact.toString()] = value
        }

    private data class CacheTask(
        val name: String,
        val write: Boolean,
        val reply: Boolean,
        val block: suspend PixivHelper.() -> List<IllustInfo>,
    )

    private data class DownloadTask(
        val name: String,
        val list: List<IllustInfo>,
        val reply: Boolean,
    )

    private var cacheChannel = Channel<CacheTask>(Channel.BUFFERED)

    private suspend fun <T, R> Iterable<T>.asyncMapIndexed(
        transform: suspend (Int, T) -> R,
    ): List<R> = withContext(Dispatchers.IO) {
        mapIndexed { index, value ->
            async {
                transform(index, value)
            }
        }.awaitAll()
    }

    private suspend fun Flow<CacheTask>.check() = transform { (name, write, reply, block) ->
        runCatching {
            block.invoke(this@PixivHelper).map {
                checkR18(it)
            }.let { list ->
                if (write) list.writeToCache()
                useArtWorkInfoMapper { mapper ->
                    list.groupBy {
                        mapper.contains(it.pid)
                    }
                }.also { (success, failure) ->
                    success?.updateToSQLite()
                    failure?.sortedBy { it.pid }?.let {
                        emit(DownloadTask(name = name, list = it, reply = reply))
                    }
                }
            }
        }.onFailure {
            logger.warning({ "预加载任务<${name}>失败" }, it)
            if (reply) sign {
                "预加载任务<${name}>失败"
            }
        }
    }

    private suspend fun Flow<DownloadTask>.download() = collect { (name, list, reply) ->
        runCatching {
            logger.verbose { "任务<${name}>有{${list.first().pid..list.last().pid}}共${list.size}个作品信息将会被尝试缓存" }
            if (reply) sign {
                "任务<${name}>有{${list.first().pid..list.last().pid}}共${list.size}个新作品等待缓存"
            }
            list.asyncMapIndexed { index, illust ->
                illust to illust.runCatching {
                    getImages()
                }.onFailure {
                    if (it !is CancellationException) {
                        logger.warning({ "任务<${name}>获取作品${index}.(${illust.pid})[${illust.title}]{${illust.pageCount}}错误" }, it)
                        if (reply) sign {
                            "任务<${name}>获取作品${index}.(${illust.pid})[${illust.title}]{${illust.pageCount}}错误, ${it.message}"
                        }
                    }
                }.isSuccess
            }.groupBy({ it.second }, { it.first }).also { (success, _) ->
                success?.saveToSQLite()
            }
        }.onSuccess { (success, _) ->
            logger.verbose { "任务<${name}>缓存完毕, 共${list.size}个新作品, 缓存成功${success?.size ?: 0}个" }
            if (reply) sign {
                "任务<${name}>缓存完毕, 共${list.size}个新作品, 缓存成功${success?.size ?: 0}个"
            }
        }.onFailure {
            logger.warning({ "任务<${name}>缓存失败, 共${list.size}个新作品" }, it)
            if (reply) sign {
                "任务<${name}>缓存失败, 共${list.size}个新作品"
            }
        }
    }

    private val cacheJob: Job = launch(Dispatchers.IO) {
        while (isActive) {
            runCatching {
                cacheChannel.receiveAsFlow().check().download()
            }.onFailure {
                if (isActive) {
                    logger.warning({ "重新加载${coroutineContext[CoroutineName]?.name}.CacheChannel" }, it)
                    cacheChannel = Channel(Channel.BUFFERED)
                }
            }
        }
    }

    suspend fun addCacheJob(
        name: String,
        write: Boolean = true,
        reply: Boolean = true,
        block: suspend PixivHelper.() -> List<IllustInfo>,
    ) = cacheChannel.send(CacheTask(
        name = name,
        write = write,
        reply = reply,
        block = block
    ))

    fun cacheStop() {
        cacheChannel.cancel()
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

    suspend fun sign(block: () -> Any?) = isActive && runCatching {
        block().let { message ->
            when (message) {
                null, Unit -> Unit
                is Message -> contact.sendMessage(message)
                is String -> contact.sendMessage(message)
                else -> contact.sendMessage(message.toString())
            }
        }
    }.onFailure { logger.warning({ "回复${contact}失败" }, it) }.isSuccess
}