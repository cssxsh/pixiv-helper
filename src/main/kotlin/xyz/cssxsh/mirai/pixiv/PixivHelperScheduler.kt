package xyz.cssxsh.mirai.pixiv

import kotlinx.coroutines.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.data.*
import kotlin.coroutines.*

object PixivHelperScheduler : CoroutineScope {
    override lateinit var coroutineContext: CoroutineContext

    private val tasks by PixivTaskData::tasks

    private val jobs: MutableMap<String, Job> = HashMap()

    private const val CHECK_DELAY = 30 * 60 * 1000L

    private fun runTimerTask(name: String, info: TimerTask) = launch {
        val millis = info.pre()
        logger.info { "${info}在${millis / 60_000}minute后开始运行" }
        delay(millis)
        while (isActive) {
            info.run(name)
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
        requireNotNull(tasks.remove(name)) { "任务不存在或者名称不完整" }
        jobs[name]?.cancel("命令任务终止")
    }

    fun start(context: CoroutineContext = EmptyCoroutineContext) {
        coroutineContext = context
        launch {
            while (isActive) {
                for ((name, info) in tasks) {
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