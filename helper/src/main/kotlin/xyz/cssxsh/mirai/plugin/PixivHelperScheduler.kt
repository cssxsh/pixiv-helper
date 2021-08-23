package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.*
import kotlin.coroutines.*

object PixivHelperScheduler : CoroutineScope {
    override lateinit var coroutineContext: CoroutineContext

    private val tasks by PixivTaskData::tasks

    private val jobs: MutableMap<String, Job> = mutableMapOf()

    private const val CHECK_DELAY = 30 * 60 * 1000L

    private fun runTimerTask(name: String, info: TimerTask) = launch {
        logger.info {
            "${info}开始运行"
        }
        info.pre()
        while (isActive) {
            runTask(name, info)
            delay(info.interval)
        }
    }

    fun setTimerTask(name: String, info: TimerTask): Unit = synchronized(jobs) {
        tasks[name] = info
        jobs.compute(name) { _, job ->
            job?.takeIf { it.isActive } ?: runTimerTask(name, info)
        }
    }

    fun removeTimerTask(name: String): Unit = synchronized(jobs) {
        tasks.remove(name)
        jobs[name]?.cancel("命令任务终止")
    }

    fun start(context: CoroutineContext = EmptyCoroutineContext) {
        coroutineContext = context
        launch {
            while (isActive) {
                tasks.forEach { (name, info) ->
                    synchronized(jobs) {
                        jobs.compute(name) { _, job ->
                            job?.takeIf { it.isActive } ?: runTimerTask(name, info)
                        }
                    }
                }
                delay(CHECK_DELAY)
            }
        }
    }

    fun stop() {
        coroutineContext.cancelChildren()
    }
}