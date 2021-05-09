package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.toPlainText
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.RemoteFile.Companion.sendFile
import xyz.cssxsh.baidu.getRapidUploadInfo
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.time.OffsetDateTime
import kotlin.properties.ReadOnlyProperty
import kotlin.time.*

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

@Serializable
enum class ContactType {
    GROUP,
    USER;
}

@Serializable
data class ContactInfo(
    @SerialName("bot")
    val bot: Long,
    @SerialName("id")
    val id: Long,
    @SerialName("type")
    val type: ContactType,
)

internal fun PixivHelper.getContactInfo(): ContactInfo = when (contact) {
    is User -> ContactInfo(
        bot = contact.bot.id,
        id = contact.id,
        type = ContactType.USER
    )
    is Group -> ContactInfo(
        bot = contact.bot.id,
        id = contact.id,
        type = ContactType.GROUP
    )
    else -> throw IllegalArgumentException("未知类型联系人")
}

internal val ContactInfo.helper by ReadOnlyProperty<ContactInfo, PixivHelper> { info, _ ->
    Bot.getInstance(info.bot).let { bot ->
        when (info.type) {
            ContactType.GROUP -> bot.getGroupOrFail(info.id)
            ContactType.USER -> bot.getFriendOrFail(info.id)
        }
    }.getHelper()
}

typealias BuildTask = suspend PixivHelper.() -> Pair<String, TimerTask>

@Serializable
sealed class TimerTask {
    abstract val interval: Long
    abstract val contact: ContactInfo

    @Serializable
    data class User(
        @SerialName("interval")
        @Contextual
        override val interval: Long,
        @SerialName("uid")
        val uid: Long,
        @SerialName("contact")
        override val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Rank(
        @SerialName("mode")
        val mode: RankMode,
        @SerialName("contact")
        override val contact: ContactInfo,
    ) : TimerTask() {
        override val interval: Long
            get() = OffsetDateTime.now().let { it.goto(next = it.toNextRank()) }.toLongMilliseconds()
    }

    @Serializable
    data class Follow(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("contact")
        override val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Recommended(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("contact")
        override val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Backup(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("contact")
        override val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Web(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("contact")
        override val contact: ContactInfo,
        @SerialName("url")
        val url: String,
        @SerialName("pattern")
        val pattern: String,
    ) : TimerTask()
}

internal suspend fun PixivHelper.subscribe(name: String, block: LoadTask) {
    block().types(WorkContentType.ILLUST).also {
        addCacheJob(name = "TimerTask(${name})", reply = false) { it }
    }.toList().flatten().filter { it.age == AgeLimit.ALL }.toSet().sortedBy { it.createAt }.let { list ->
        list.forEachIndexed { index, illust ->
            delay(interval)
            send {
                "Task: $name (${index + 1}/${list.size})\n".toPlainText() + buildMessageByIllust(illust = illust)
            }
            useMappers {
                it.statistic.addHistory(StatisticTaskInfo(
                    task = name,
                    pid = illust.pid,
                    timestamp = OffsetDateTime.now().toEpochSecond()
                ))
            }
        }
    }
}

private const val RANK_HOUR = 12

internal fun OffsetDateTime.toNextRank(): OffsetDateTime =
    (if (hour < RANK_HOUR) this else plusDays(1)).withHour(RANK_HOUR).withMinute(0).withSecond(0)

internal fun OffsetDateTime.goto(next: OffsetDateTime) = (next.toEpochSecond() - toEpochSecond()).seconds

private val random get() = (10..60).random().minutes

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
        info.contact.helper.subscribe(name = name) {
            getUserIllusts(detail = userDetail(uid = info.uid), limit = PAGE_SIZE).isToday().notHistory(task = name)
        }
    }
    is TimerTask.Rank -> {
        info.contact.helper.subscribe(name = name) {
            getRank(mode = info.mode, limit = TASK_LOAD).notHistory(task = name)
        }
    }
    is TimerTask.Follow -> {
        info.contact.helper.subscribe(name) {
            getFollowIllusts(limit = TASK_LOAD).notHistory(task = name)
        }
    }
    is TimerTask.Recommended -> {
        info.contact.helper.subscribe(name) {
            getRecommended(limit = TASK_LOAD).eros().notHistory(task = name)
        }
    }
    is TimerTask.Backup -> {
        val helper = info.contact.helper
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
        info.contact.helper.subscribe(name) {
            getListIllusts(set = loadWeb(url = Url(info.url), regex = info.pattern.toRegex())).notHistory(task = name)
        }
    }
}


