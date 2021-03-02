package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.PixivTaskData
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import kotlin.time.*

object PixivHelperScheduler {

    private val jobs: MutableMap<String, Job> = mutableMapOf()

    private var check: Job? = null

    private const val CACHE_DELAY = 10

    private fun runTask(name: String, info: TimerTask) = PixivHelperPlugin.launch {
        logger.info {
            "Task($name)开始运行, $info"
        }
        info.delay()
        while (isActive) {
            launch {
                info.run(task = name)
            }
            info.delay()
        }
    }

    fun setTimerTask(name: String, info: TimerTask): Unit = synchronized(jobs) {
        PixivTaskData.tasks[name] = info
        jobs.put(name, runTask(name, info))?.cancel()
    }

    fun start() {
        check = PixivHelperPlugin.launch {
            while (isActive) {
                PixivTaskData.tasks.forEach { (name, info) ->
                    synchronized(jobs) {
                        jobs.compute(name) { _, job ->
                            job?.takeIf { it.isActive } ?: runTask(name, info)
                        }
                    }
                }
                delay(CACHE_DELAY.minutes)
            }
        }
    }

    fun stop() {
        check?.cancel()
    }
}