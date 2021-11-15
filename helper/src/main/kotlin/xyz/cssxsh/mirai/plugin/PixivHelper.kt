package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScopeContext
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.auth.*
import java.time.*
import kotlin.coroutines.*

/**
 * 助手实例
 */
class PixivHelper(val contact: Contact) : PixivAuthClient() {

    @OptIn(ConsoleExperimentalApi::class)
    override val coroutineContext: CoroutineContext by lazy {
        contact.childScopeContext("PixivHelper:${contact}")
    }

    override var config: PixivConfig by ConfigDelegate

    override fun config(block: PixivConfig.() -> Unit): PixivConfig = super.config(block).also { config = it }

    override var authInfo: AuthResult? by AuthResultDelegate

    internal val uid get() = authInfo?.user?.uid

    public override var expires: OffsetDateTime by ExpiresTimeDelegate

    public override val mutex: Mutex by MutexDelegate

    override val ignore: Ignore = Ignore(helper = this)

    override val timeout: Long get() = TimeoutApi

    internal var link: Boolean by LinkDelegate

    internal var tag: Boolean by TagDelegate

    internal var attr: Boolean by AttrDelegate

    internal var max: Int by MaxDelegate

    internal var model: SendModel by ModelDelegate

    private var cacheChannel = Channel<CacheTask>(Channel.BUFFERED)

    private val cacheJob = launch(CoroutineName(name = "PixivHelper:${contact}#CacheTask")) {
        while (isActive) {
            try {
                logger.info { "PixivHelper:${contact}#CacheTask start" }
                supervisorScope {
                    cacheChannel.consumeAsFlow().save().download(CacheJump).await(CacheCapacity)
                }
            } catch (e: Throwable) {
                logger.warning { "PixivHelper:${contact}#CacheTask $e" }
            }
            cacheChannel = Channel(Channel.BUFFERED)
        }
    }

    private suspend fun Flow<CacheTask>.save() = transform { (name, write, reply, block) ->
        try {
            block.invoke(this@PixivHelper).collect { list ->
                if (list.isEmpty()) return@collect
                try {
                    val (success, failure) = list.groupBy { it.pid in ArtWorkInfo }
                    list.replicate()
                    if (success != null && write) {
                        success.write()
                    }
                    if (failure != null) {
                        val downloads = failure.write().filter { it.isEro() }.sortedBy { it.pid }
                        this@transform.emit(DownloadTask(name = name, list = downloads, reply = reply))
                    }
                } catch (e: Throwable) {
                    logger.warning({ "预加载任务<${name}>失败" }, e)
                }
            }
        } catch (e: Throwable) {
            logger.warning({ "预加载任务<${name}>失败" }, e)
            if (reply) send {
                "预加载任务<${name}>失败"
            }
        }
    }

    private suspend fun Flow<DownloadTask>.download(jump: Boolean = false) = transform { (name, list, reply) ->
        if (jump) return@transform
        if (list.isEmpty()) return@transform
        logger.verbose {
            "任务<${name}>有{${list.first().pid..list.last().pid}}共${list.size}个作品信息将会被尝试缓存"
        }
        if (reply) send {
            "任务<${name}>有{${list.first().pid..list.last().pid}}共${list.size}个新作品等待缓存"
        }
        emit(list.map { illust ->
            async {
                try {
                    when (illust.type) {
                        WorkContentType.ILLUST -> illust.getImages()
                        WorkContentType.UGOIRA -> illust.getUgoira()
                        WorkContentType.MANGA -> Unit
                    }
                } catch (e: CancellationException) {
                    return@async
                } catch (e: Throwable) {
                    logger.warning({
                        "任务<${name}>获取作品(${illust.pid})[${illust.title}]{${illust.pageCount}}错误"
                    }, e)
                    if (reply) send {
                        "任务<${name}>获取作品(${illust.pid})[${illust.title}]{${illust.pageCount}}错误, ${e.message}"
                    }
                }
            }
        })
    }

    private suspend fun Flow<List<Deferred<*>>>.await(capacity: Int = 3) = buffer(capacity).collect { it.awaitAll() }

    suspend fun addCacheJob(name: String, write: Boolean = true, reply: Boolean = true, block: LoadTask) {
        cacheChannel.send(CacheTask(name = name, write = write, reply = reply, block = block))
    }

    fun cacheStop() {
        launch(SupervisorJob()) {
            cacheJob.cancelChildren(CancellationException("指令终止"))
        }
    }

    suspend fun send(block: suspend () -> Any?): Boolean = supervisorScope {
        isActive && try {
            when (val message = block()) {
                null, Unit -> Unit
                is ForwardMessage -> {
                    check(message.nodeList.size <= 200) {
                        throw MessageTooLargeException(
                            contact, message, message,
                            "ForwardMessage allows up to 200 nodes, but found ${message.nodeList.size}"
                        )
                    }
                    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
                    contact.sendMessage(message + net.mamoe.mirai.internal.message.IgnoreLengthCheck)
                }
                is Message -> contact.sendMessage(message)
                is String -> contact.sendMessage(message)
                else -> contact.sendMessage(message.toString())
            }
            true
        } catch (e: Throwable) {
            logger.warning({ "回复${contact}失败" }, e)
            false
        }
    }
}