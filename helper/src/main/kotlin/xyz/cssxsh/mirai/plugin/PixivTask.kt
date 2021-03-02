package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.data.apps.*
import java.time.OffsetDateTime
import kotlin.time.*

internal data class CacheTask(
    val name: String,
    val write: Boolean,
    val reply: Boolean,
    val block: suspend PixivHelper.() -> List<IllustInfo>,
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

internal fun ContactInfo.getHelperOrNull() = Bot.findInstance(bot)?.let { bot ->
    when (type) {
        ContactType.GROUP -> bot.getGroup(id)
        ContactType.USER -> bot.getFriend(id) ?: bot.getStranger(id)
    }
}?.let { PixivHelperManager[it] }

@Serializable
sealed class TimerTask {
    abstract var last: Long

    @Serializable
    data class User(
        @SerialName("last")
        override var last: Long = OffsetDateTime.now().toEpochSecond(),
        @SerialName("interval")
        val interval: Long,
        @SerialName("uid")
        val uid: Long,
        @SerialName("contact")
        val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Rank(
        @SerialName("last")
        override var last: Long = OffsetDateTime.now().toEpochSecond(),
        @SerialName("mode")
        val mode: RankMode,
        @SerialName("contact")
        val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Follow(
        @SerialName("last")
        override var last: Long = OffsetDateTime.now().toEpochSecond(),
        @SerialName("interval")
        val interval: Long,
        @SerialName("contact")
        val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Backup(
        @SerialName("last")
        override var last: Long = OffsetDateTime.now().toEpochSecond(),
        @SerialName("interval")
        val interval: Long,
    ) : TimerTask()
}

private val SEND_DELAY = (3).minutes

internal suspend fun PixivHelper.subscribe(
    name: String,
    last: Long,
    block: suspend PixivHelper.() -> List<IllustInfo>,
): IllustInfo? {
    val illusts = block()
    addCacheJob(name = "TimerTask(${name})", reply = false) { illusts }
    illusts.nomanga().filter { it.createAt.toEpochSecond() > last && it.isR18().not() }.forEach { illust ->
        delay(SEND_DELAY)
        buildMessageByIllust(illust = illust, save = false).forEach { sign { it } }
    }
    return illusts.maxByOrNull { it.createAt }
}

private const val RANK_HOUR = 12

internal fun OffsetDateTime.toNextRank(): OffsetDateTime =
    (if (hour < RANK_HOUR) this else plusDays(1)).withHour(RANK_HOUR).withMinute(0).withSecond(0)

internal suspend fun TimerTask.delay() {
    when (this) {
        is TimerTask.User -> {
            delay((0..interval).random())
        }
        is TimerTask.Rank -> {
            delay((OffsetDateTime.now().toNextRank().toEpochSecond() - last))
        }
        is TimerTask.Follow -> {
            delay((0..interval).random())
        }
        is TimerTask.Backup -> {
            delay((0..interval).random())
        }
    }
}

internal suspend fun TimerTask.run(task: String) {
    when (this) {
        is TimerTask.User -> {
            contact.getHelperOrNull()?.let { helper ->
                helper.subscribe(name = task, last = last) {
                    getUserIllusts(uid = uid, limit = 30L) // one page
                }
            }?.let {
                last = it.createAt.toEpochSecond()
            }
        }
        is TimerTask.Rank -> {
            contact.getHelperOrNull()?.let { helper ->
                helper.subscribe(name = task, last = last) {
                    getRank(mode = mode, date = null, limit = 180L)
                }
            }?.let {
                last = it.createAt.toEpochSecond()
            }
        }
        is TimerTask.Follow -> {
            contact.getHelperOrNull()?.let { helper ->
                helper.subscribe(name = task, last = last) {
                    getFollowIllusts(limit = 90L)
                }
            }?.let {
                last = it.createAt.toEpochSecond()
            }
        }
        is TimerTask.Backup -> {
            PixivZipper.compressData(list = getBackupList()).forEach { file ->
                runCatching {
                    BaiduNetDiskUpdater.uploadFile(file)
                }.onSuccess { info ->
                    PixivHelperPlugin.logger.info { "[${file}]上传成功: $info" }
                }.onFailure {
                    PixivHelperPlugin.logger.warning({ "[${file}]上传失败" }, it)
                }
            }
            last = OffsetDateTime.now().toEpochSecond()
        }
    }
}


