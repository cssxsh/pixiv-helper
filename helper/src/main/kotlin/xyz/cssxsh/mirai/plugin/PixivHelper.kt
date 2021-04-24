package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.auth.*
import java.time.OffsetDateTime

/**
 * 助手实例
 */
class PixivHelper(val contact: Contact) : SimplePixivClient(
    parentCoroutineContext = PixivHelperPlugin.coroutineContext,
    coroutineName = "PixivHelper:${contact}",
    config = DEFAULT_PIXIV_CONFIG // This config is not use
) {

    override var config: PixivConfig by PixivHelperDelegate.config

    override fun config(block: PixivConfig.() -> Unit): PixivConfig = super.config(block).also { config = it }

    override var authInfo: AuthResult? by PixivHelperDelegate.auth

    public override var expiresTime: OffsetDateTime by PixivHelperDelegate.expires

    override val apiIgnore: suspend (Throwable) -> Boolean get() = PixivApiIgnore

    var link: Boolean by PixivHelperDelegate.link

    private var cacheChannel = Channel<CacheTask>(Channel.BUFFERED)

    private suspend fun Flow<CacheTask>.check() = transform { (name, write, reply, block) ->
        runCatching {
            block.invoke(this@PixivHelper).onEach { list ->
                if (write && list.isNotEmpty()) {
                    list.writeToCache()
                }
            }.collect { list ->
                useMappers { mappers ->
                    list.groupBy {
                        mappers.artwork.contains(it.pid)
                    }
                }.also { (success, failure) ->
                    success?.updateToSQLite()
                    failure?.sortedBy { it.pid }?.let {
                        this@transform.emit(DownloadTask(name = name, list = it, reply = reply))
                    }
                }
            }
        }.onFailure {
            logger.warning({ "预加载任务<${name}>失败" }, it)
            if (reply) send {
                "预加载任务<${name}>失败"
            }
        }
    }

    private suspend fun Flow<DownloadTask>.download() = collect { (name, list, reply) ->
        logger.verbose {
            "任务<${name}>有{${list.first().pid..list.last().pid}}共${list.size}个作品信息将会被尝试缓存"
        }
        if (reply) send {
            "任务<${name}>有{${list.first().pid..list.last().pid}}共${list.size}个新作品等待缓存"
        }
        list.map { illust ->
            async {
                illust to illust.runCatching {
                    getImages()
                }.onFailure {
                    if (it.isNotCancellationException()) {
                        logger.warning({
                            "任务<${name}>获取作品(${illust.pid})[${illust.title}]{${illust.pageCount}}错误"
                        }, it)
                        if (reply) send {
                            "任务<${name}>获取作品(${illust.pid})[${illust.title}]{${illust.pageCount}}错误, ${it.message}"
                        }
                    }
                }.isSuccess
            }
        }.runCatching {
            awaitAll().groupBy({ it.second }, { it.first }).let { (success, _) ->
                success?.saveToSQLite()
            }
        }
    }

    init {
        launch(CoroutineName("PixivHelper:${contact}#CacheTask")) {
            while (isActive) {
                runCatching {
                    logger.info { "PixivHelper:${contact}#CacheTask start"  }
                    cacheChannel.consumeAsFlow().check().download()
                }
                cacheChannel = Channel(Channel.BUFFERED)
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
        cacheChannel.close()
        cacheChannel.cancel()
    }

    var followJob: Job? = null

    override suspend fun auth(
        grant: GrantType,
        config: PixivConfig,
        time: OffsetDateTime,
    ) = super.auth(grant = grant, config = config, time = time).also {
        when (grant) {
            GrantType.PASSWORD -> logger.info {
                "User: ${it.user.name}#${it.user.uid} AccessToken: ${it.accessToken} by Account: ${config.account}, ExpiresTime: $expiresTime"
            }
            GrantType.REFRESH_TOKEN -> logger.info {
                "User: ${it.user.name}#${it.user.uid} AccessToken: ${it.accessToken} by RefreshToken: ${config.refreshToken}, ExpiresTime: $expiresTime"
            }
        }
    }

    suspend fun send(block: () -> Any?) = isActive && runCatching {
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