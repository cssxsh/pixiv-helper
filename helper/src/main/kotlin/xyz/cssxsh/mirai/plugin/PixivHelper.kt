package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
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

    override var config: PixivConfig by ConfigDelegate

    override fun config(block: PixivConfig.() -> Unit): PixivConfig = super.config(block).also { config = it }

    override var authInfo: AuthResult? by AuthResultDelegate

    public override var expiresTime: OffsetDateTime by ExpiresTimeDelegate

    override val apiIgnore: Ignore get() = PixivApiIgnore

    var link: Boolean by LinkDelegate

    private var cacheChannel = Channel<CacheTask>(Channel.BUFFERED)

    private suspend fun Flow<CacheTask>.save() = transform { (name, write, reply, block) ->
        runCatching {
            block.invoke(this@PixivHelper).onEach { list ->
                if (write && list.isNotEmpty()) {
                    list.write()
                }
            }.collect { list ->
                useMappers { mappers ->
                    list.groupBy {
                        mappers.artwork.contains(it.pid)
                    }
                }.also { (success, failure) ->
                    success?.update()
                    failure?.sortedBy { it.pid }?.let {
                        it.save()
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

    private suspend fun Flow<DownloadTask>.download() = transform { (name, list, reply) ->
        logger.verbose {
            "任务<${name}>有{${list.first().pid..list.last().pid}}共${list.size}个作品信息将会被尝试缓存"
        }
        if (reply) send {
            "任务<${name}>有{${list.first().pid..list.last().pid}}共${list.size}个新作品等待缓存"
        }
        list.map { illust ->
            async {
                illust.runCatching {
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
                }
            }
        }.let {
            emit(it)
        }
    }

    private suspend fun Flow<List<Deferred<*>>>.await() = collect { it.awaitAll() }

    init {
        launch(CoroutineName(name = "PixivHelper:${contact}#CacheTask")) {
            while (isActive) {
                runCatching {
                    logger.info { "PixivHelper:${contact}#CacheTask start"  }
                    cacheChannel.consumeAsFlow().save().download().await()
                }
                cacheChannel = Channel(Channel.BUFFERED)
            }
        }
    }

    fun addCacheJob(name: String, write: Boolean = true, reply: Boolean = true, block: LoadTask) = launch {
        cacheChannel.send(CacheTask(
            name = name,
            write = write,
            reply = reply,
            block = block
        ))
    }

    fun cacheStop() {
        cacheChannel.close()
        cacheChannel.cancel()
    }

    var followJob: Job? = null

    var play: Job? = null

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

    suspend fun send(block: () -> Any?): Boolean {
        return isActive && runCatching {
            block().let { message ->
                when (message) {
                    null, Unit -> Unit
                    is Message -> contact.sendMessage(message)
                    is String -> contact.sendMessage(message)
                    else -> contact.sendMessage(message.toString())
                }
            }
        }.onFailure {
            logger.warning({ "回复${contact}失败" }, it)
        }.isSuccess
    }
}