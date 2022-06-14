package xyz.cssxsh.mirai.pixiv

import kotlinx.coroutines.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.task.*
import java.util.concurrent.*
import kotlin.coroutines.*

public object PixivCacheLoader : CoroutineScope {
    private val logger by lazy { MiraiLogger.Factory.create(this::class, identity = "pixiv-cache-loader") }

    override val coroutineContext: CoroutineContext =
        CoroutineName(name = "pixiv-cache-loader") + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
            logger.warning({ "$throwable in $context" }, throwable)
        }

    private val jobs: MutableMap<String, Job> = ConcurrentHashMap()

    public fun cache(name: String, block: PixivCacheTask.() -> Unit) {
        if (jobs[name]?.isActive == true) throw IllegalArgumentException("$name 任务已存在")

        val task = PixivCacheTask(name = name).apply(block)
        jobs[name] = launch {
            try {
                task.flow.toString()
                TODO()
            } catch (_: Throwable) {

            } finally {
                jobs.remove(name)
            }
        }
    }

    public fun detail(): String {
        TODO()
    }

    public fun stop(name: String) {
        TODO()
    }
}