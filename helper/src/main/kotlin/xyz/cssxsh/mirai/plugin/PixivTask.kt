package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.RemoteFile.Companion.sendFile
import xyz.cssxsh.baidu.*
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.mirai.plugin.data.SendModel
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.time.*
import kotlin.properties.*

internal data class CacheTask(
    val name: String,
    val write: Boolean,
    val reply: Boolean,
    val block: LoadTask,
)

internal data class DownloadTask(
    val name: String,
    val list: List<IllustInfo>,
    val reply: Boolean,
)

internal val TimerTask.helper by ReadOnlyProperty<TimerTask, PixivHelper> { task, _ ->
    requireNotNull(findContact(task.delegate)) { "找不到联系人" }.getHelper()
}

typealias BuildTask = suspend PixivHelper.() -> Pair<String, TimerTask>

@Serializable
sealed class TimerTask {
    abstract val interval: Long
    abstract val delegate: Long

    @Serializable
    data class User(
        @SerialName("interval")
        @Contextual
        override val interval: Long,
        @SerialName("uid")
        val uid: Long,
        @SerialName("delegate")
        override val delegate: Long,
    ) : TimerTask()

    @Serializable
    data class Rank(
        @SerialName("mode")
        val mode: RankMode,
        @SerialName("delegate")
        override val delegate: Long,
    ) : TimerTask() {
        override val interval: Long
            get() = OffsetDateTime.now().let { it.goto(it.toNextRank()) } * 1000L
    }

    @Serializable
    data class Follow(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("delegate")
        override val delegate: Long,
    ) : TimerTask()

    @Serializable
    data class Recommended(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("delegate")
        override val delegate: Long,
    ) : TimerTask()

    @Serializable
    data class Backup(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("delegate")
        override val delegate: Long,
    ) : TimerTask()

    @Serializable
    data class Web(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("delegate")
        override val delegate: Long,
        @SerialName("url")
        val url: String,
        @SerialName("pattern")
        val pattern: String,
    ) : TimerTask()

    @Serializable
    data class Trending(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("delegate")
        override val delegate: Long,
        @SerialName("times")
        val times: Int,
    ) : TimerTask()
}

private class TaskDisplayStrategy(val task: String, val size: Int) : ForwardMessage.DisplayStrategy {
    override fun generateTitle(forward: RawForwardMessage): String = task
    override fun generateSummary(forward: RawForwardMessage): String = "查看${task}推送消息"
}

private suspend fun PixivHelper.subscribe(name: String, block: LoadTask) {
    val flow = block().eros(mark = false).notHistory(task = name)
    addCacheJob(name = "TimerTask(${name})", reply = false) { flow }
    val list = flow.toList().flatten().filter { it.age == AgeLimit.ALL }.distinctBy { it.pid }
    val forward = ForwardMessageBuilder(contact).apply {
        displayStrategy = TaskDisplayStrategy(task = name, size = list.size)
    }

    for ((index, illust) in list.sortedBy { it.createAt }.withIndex()) {
        if (isActive.not()) break

        val message = "Task: $name (${index + 1}/${list.size})\n".toPlainText() + buildMessageByIllust(illust = illust)
        if (TaskForward) {
            with(forward) {
                contact.bot.says(message)
            }
        } else {
            delay(TaskSendInterval * 1000L)
            send {
                message
            }
        }

        StatisticTaskInfo(
            task = name,
            pid = illust.pid,
            timestamp = OffsetDateTime.now().toEpochSecond()
        ).replicate()
    }

    if (TaskForward) {
        send {
            forward.build()
        }
    }
}

private suspend fun PixivHelper.trending(name: String, times: Int = 1) {
    val flow = getTrending(times)
    addCacheJob(name = "TimerTask(${name})", reply = false) { flow.map { it.map(TrendIllust::illust) } }
    val list = flow.toList().flatten().filter {
        it.illust.isEro(false) && (name to it.illust.pid) !in StatisticTaskInfo
    }
    val forward = ForwardMessageBuilder(contact).apply {
        displayStrategy = TaskDisplayStrategy(task = name, size = list.size)
    }

    for ((index, trending) in list.withIndex()) {
        if (isActive.not()) break

        val message = "Task: $name (${index + 1}/${list.size}) [${trending.tag}]\n".toPlainText() +
            buildMessageByIllust(illust = trending.illust)
        if (TaskForward) {
            with(forward) {
                contact.bot.says(message)
            }
        } else {
            delay(TaskSendInterval * 1000L)
            send {
                message
            }
        }
        StatisticTaskInfo(
            task = name,
            pid = trending.illust.pid,
            timestamp = OffsetDateTime.now().toEpochSecond()
        ).replicate()
    }

    if (TaskForward) {
        send {
            forward.build()
        }
    }
}

private const val RANK_HOUR = 12

internal fun OffsetDateTime.toNextRank(): OffsetDateTime =
    (if (hour < RANK_HOUR) this else plusDays(1)).withHour(RANK_HOUR).withMinute(0).withSecond(0)

internal fun OffsetDateTime.goto(next: OffsetDateTime) = next.toEpochSecond() - toEpochSecond()

private val random get() = (10..60).random() * 60 * 1000L

internal suspend fun TimerTask.pre(): Unit = when (this) {
    is TimerTask.User -> {
        delay(random)
    }
    is TimerTask.Rank -> {
        delay(random)
    }
    is TimerTask.Follow -> {
        delay(random)
    }
    is TimerTask.Recommended -> {
        delay(interval)
    }
    is TimerTask.Backup -> {
        delay(interval)
    }
    is TimerTask.Web -> {
        delay(random)
    }
    is TimerTask.Trending -> {
        delay(interval)
    }
}

internal suspend fun TimerTask.run(name: String) = when (this) {
    is TimerTask.User -> {
        helper.subscribe(name) {
            getUserIllusts(detail = userDetail(uid = uid), limit = PAGE_SIZE).isToday()
        }
    }
    is TimerTask.Rank -> {
        helper.subscribe(name) {
            getRank(mode = mode).eros()
        }
    }
    is TimerTask.Follow -> {
        helper.subscribe(name) {
            getFollowIllusts(limit = TASK_LOAD).isToday()
        }
    }
    is TimerTask.Recommended -> {
        helper.subscribe(name) {
            getRecommended(limit = PAGE_SIZE).eros()
        }
    }
    is TimerTask.Backup -> {
        val contact = helper.contact
        PixivZipper.compressData(list = getBackupList()).forEach { file ->
            if (contact is FileSupported) {
                contact.sendMessage("${file.name} 压缩完毕，开始上传到群文件")
                runCatching {
                    contact.sendFile(path = file.name, file = file)
                }.onFailure {
                    contact.sendMessage("上传失败: ${it.message}")
                }
            } else {
                contact.sendMessage("${file.name} 压缩完毕，开始上传到百度云")
                runCatching {
                    BaiduNetDiskUpdater.uploadFile(file)
                }.onSuccess {
                    val code = file.getRapidUploadInfo().format()
                    logger.info { "[${file.name}]上传成功: 百度云标准码${code} " }
                    contact.sendMessage("[${file.name}]上传成功，百度云标准码: $code")
                }.onFailure {
                    logger.warning({ "[${file.name}]上传失败" }, it)
                    contact.sendMessage("[${file.name}]上传失败, ${it.message}")
                }
            }
        }
    }
    is TimerTask.Web -> {
        helper.subscribe(name) {
            getListIllusts(set = loadWeb(url = Url(url), regex = pattern.toRegex()))
        }
    }
    is TimerTask.Trending -> {
        helper.trending(name = name, times = times)
    }
}


