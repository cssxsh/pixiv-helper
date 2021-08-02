package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.toPlainText
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.RemoteFile.Companion.sendFile
import xyz.cssxsh.baidu.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.time.OffsetDateTime
import kotlin.properties.ReadOnlyProperty

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
}

internal suspend fun PixivHelper.subscribe(name: String, block: LoadTask) {
    val flow = block().types(WorkContentType.ILLUST).notHistory(task = name)
    addCacheJob(name = "TimerTask(${name})", reply = false) { flow }
    val list = flow.toList().flatten().filter { it.age == AgeLimit.ALL }.associateBy { it.pid }.values
    list.sortedBy { it.createAt }.forEachIndexed { index, illust ->
        delay(SendInterval * 1000L)
        send {
            "Task: $name (${index + 1}/${list.size})\n".toPlainText() + buildMessageByIllust(illust = illust)
        }
        StatisticTaskInfo(
            task = name,
            pid = illust.pid,
            timestamp = OffsetDateTime.now().toEpochSecond()
        ).saveOrUpdate()
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
}

internal suspend fun runTask(name: String, info: TimerTask) = when (info) {
    is TimerTask.User -> {
        info.helper.subscribe(name = name) {
            getUserIllusts(detail = userDetail(uid = info.uid), limit = PAGE_SIZE).isToday()
        }
    }
    is TimerTask.Rank -> {
        info.helper.subscribe(name = name) {
            getRank(mode = info.mode).eros()
        }
    }
    is TimerTask.Follow -> {
        info.helper.subscribe(name) {
            getFollowIllusts(limit = TASK_LOAD).isToday()
        }
    }
    is TimerTask.Recommended -> {
        info.helper.subscribe(name) {
            getRecommended(limit = PAGE_SIZE).eros()
        }
    }
    is TimerTask.Backup -> {
        val helper = info.helper
        PixivZipper.compressData(list = getBackupList()).forEach { file ->
            if (helper.contact is FileSupported) {
                helper.contact.sendMessage("${file.name} 压缩完毕，开始上传到群文件")
                runCatching {
                    (helper.contact).sendFile(path = file.name, file = file)
                }.onFailure {
                    helper.contact.sendMessage("上传失败: ${it.message}")
                }
            } else {
                helper.contact.sendMessage("${file.name} 压缩完毕，开始上传到百度云")
                runCatching {
                    BaiduNetDiskUpdater.uploadFile(file)
                }.onSuccess {
                    val code = file.getRapidUploadInfo().format()
                    logger.info { "[${file.name}]上传成功: 百度云标准码${code} " }
                    helper.contact.sendMessage("[${file.name}]上传成功，百度云标准码: $code")
                }.onFailure {
                    logger.warning({ "[${file.name}]上传失败" }, it)
                    helper.contact.sendMessage("[${file.name}]上传失败, ${it.message}")
                }
            }
        }
    }
    is TimerTask.Web -> {
        info.helper.subscribe(name) {
            getListIllusts(set = loadWeb(url = Url(info.url), regex = info.pattern.toRegex()))
        }
    }
}


