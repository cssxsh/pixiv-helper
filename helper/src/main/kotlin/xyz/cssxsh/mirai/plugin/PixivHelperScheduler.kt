package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.PixivTaskData
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import kotlin.time.*

object PixivHelperScheduler {

    private val jobs: MutableMap<String, Job> = mutableMapOf()

    private var check: Job? = null

    private fun runTimerTask(name: String, info: TimerTask) = PixivHelperPlugin.launch {
        logger.info {
            "${info}开始运行"
        }
        info.delay()
        while (isActive) {
            launch {
                runTask(name = name, info = info)
            }
            info.delay()
        }
    }

    fun setTimerTask(name: String, info: TimerTask): Unit = synchronized(jobs) {
        PixivTaskData.tasks[name] = info
        jobs.put(name, runTimerTask(name, info))?.cancel()
    }

    fun start() {
        check = PixivHelperPlugin.launch {
            while (isActive) {
                PixivTaskData.tasks.forEach { (name, info) ->
                    synchronized(jobs) {
                        jobs.compute(name) { _, job ->
                            job?.takeIf { it.isActive } ?: runTimerTask(name, info)
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        check?.cancel()
    }
}