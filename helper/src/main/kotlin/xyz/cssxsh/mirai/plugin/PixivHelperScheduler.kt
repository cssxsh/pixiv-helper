package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.utils.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.cssxsh.mirai.plugin.data.PixivTaskData
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.tools.*
import java.time.OffsetDateTime
import kotlin.time.*

object PixivHelperScheduler {

    private val jobs: MutableMap<String, Job> = mutableMapOf()

    private var check: Job? = null

    private const val LOAD_LIMIT = 6 * 30L

    private fun runTask(name: String, info: TimerTask) = PixivHelperPlugin.launch {
        logger.info {
            "任务开始运行, $info"
        }
        while (isActive) {
            when(info) {
                is TimerTask.User -> {
                    launch {
                        info.contact.getHelperOrNull()?.let { helper ->
                            helper.subscribe(name = name, last = info.last) {
                                getUserIllusts(uid = info.uid, limit = LOAD_LIMIT)
                            }
                        }?.let {
                            info.last = it.createAt.toEpochSecond()
                        }
                    }
                    delay(info.interval)
                }
                is TimerTask.Rank -> {
                    val last = info.contact.getHelperOrNull()?.let { helper ->
                        helper.subscribe(name = name, last = info.last) {
                            getRank(mode = info.mode, date = null, limit = LOAD_LIMIT)
                        }
                    }?.createAt ?: OffsetDateTime.now()
                    val next = last.toNextRank()
                    delay((next.toEpochSecond() - last.toEpochSecond()).seconds)
                }
                is TimerTask.Follow -> {
                    launch {
                        info.contact.getHelperOrNull()?.let { helper ->
                            helper.subscribe(name = name, last = info.last) {
                                getFollowIllusts(limit = LOAD_LIMIT)
                            }?.let {
                                info.last = it.createAt.toEpochSecond()
                            }
                        }
                    }
                    delay(info.interval)
                }
                is TimerTask.Backup -> {
                    launch {
                        PixivZipper.compressData(list = getBackupList()).forEach { file ->
                            runCatching {
                                BaiduNetDiskUpdater.uploadFile(file)
                            }.onSuccess { info ->
                                logger.info { "[${file}]上传成功: $info" }
                            }.onFailure {
                                logger.warning({ "[${file}]上传失败" }, it)
                            }
                        }
                    }
                    delay(info.interval)
                }
            }
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
                delay((10).minutes)
            }
        }
    }

    fun stop() {
        check?.cancel()
    }
}