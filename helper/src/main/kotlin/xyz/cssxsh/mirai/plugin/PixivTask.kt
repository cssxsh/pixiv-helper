package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.*
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
        buildMessageByIllust(illust = illust, save = false).forEach { sign { it } }
        delay(SEND_DELAY)
    }
    return illusts.maxByOrNull { it.createAt }
}

private const val RANK_HOUR = 8

internal fun OffsetDateTime.toNextRank(): OffsetDateTime =
    (if (hour < RANK_HOUR) this else plusDays(1)).withHour(RANK_HOUR).withMinute(0)




