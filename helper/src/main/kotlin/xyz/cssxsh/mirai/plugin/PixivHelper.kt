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
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.client.*
import xyz.cssxsh.pixiv.data.*
import xyz.cssxsh.pixiv.data.apps.*
import java.time.OffsetDateTime
import kotlin.time.*

/**
 * 助手实例
 */
class PixivHelper(val contact: Contact) : SimplePixivClient(
    parentCoroutineContext = PixivHelperPlugin.coroutineContext,
    coroutineName = "PixivHelper:${contact}",
    config = DEFAULT_PIXIV_CONFIG // This config is not use
) {

    override var config: PixivConfig by ConfigDelegate(contact)

    override fun config(block: PixivConfig.() -> Unit): PixivConfig = super.config(block).also { config = it }

    override var authInfo: AuthResult? by AuthInfoDelegate(contact)

    public override var expiresTime: OffsetDateTime by ExpiresTimeDelegate(contact)

    override val apiIgnore: suspend (Throwable) -> Boolean get() = PixivApiIgnore

    var simpleInfo: Boolean by SimpleInfoDelegate(contact)

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
            block.invoke(this@PixivHelper).map { list ->
                list.map {
                    checkR18(it)
                }
            }.onEach { list ->
                if (write && list.isNotEmpty()) {
                    list.writeToCache()
                }
            }.collect { list ->
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
                    if (it.isNotCancellationException()) {
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

    private val cacheJob: Job = launch(CoroutineName("PixivHelper:${contact}#CacheTask") + Dispatchers.IO) {
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
        block: LoadTask,
    ) = cacheChannel.send(CacheTask(
        name = name,
        write = write,
        reply = reply,
        block = block
    ))

    fun cacheStop() {
        cacheJob.cancel()
        cacheChannel.cancel()
    }

    var followJob: Job? = null

    override suspend fun refresh(token: String) = super.refresh(token).also {
        logger.info { "User: ${it.user.name}#${it.user.uid} AccessToken: ${it.accessToken} by RefreshToken: $token, ExpiresTime: $expiresTime" }
    }

    override suspend fun login(mailOrPixivID: String, password: String) = super.login(mailOrPixivID, password).also {
        logger.info { "User: ${it.user.name}#${it.user.uid} AccessToken: ${it.accessToken} by Account: $mailOrPixivID, ExpiresTime: $expiresTime" }
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