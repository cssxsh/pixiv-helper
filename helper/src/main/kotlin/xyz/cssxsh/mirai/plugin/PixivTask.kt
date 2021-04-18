package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.message.data.toPlainText
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.PixivTaskData
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.data.apps.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.*

typealias LoadTask = suspend PixivHelper.() -> Flow<List<IllustInfo>>

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

internal fun ContactInfo.getHelper() = Bot.getInstance(bot).let { bot ->
    when (type) {
        ContactType.GROUP -> bot.getGroupOrFail(id)
        ContactType.USER -> bot.getFriend(id) ?: bot.getStrangerOrFail(id)
    }
}.let { PixivHelperManager[it] }

@Serializable
sealed class TimerTask {
    abstract var last: OffsetDateTime
    abstract val interval: Long

    @Serializer(OffsetDateTime::class)
    object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {

        private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(OffsetDateTime::class.qualifiedName!!, PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): OffsetDateTime =
            OffsetDateTime.parse(decoder.decodeString(), formatter)

        override fun serialize(encoder: Encoder, value: OffsetDateTime) =
            encoder.encodeString(formatter.format(value))
    }

    @Serializable
    data class User(
        @SerialName("last")
        @Serializable(OffsetDateTimeSerializer::class)
        override var last: OffsetDateTime = OffsetDateTime.now(),
        @SerialName("interval")
        override val interval: Long,
        @SerialName("uid")
        val uid: Long,
        @SerialName("contact")
        val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Rank(
        @SerialName("last")
        @Serializable(OffsetDateTimeSerializer::class)
        override var last: OffsetDateTime = OffsetDateTime.now(),
        @SerialName("mode")
        val mode: RankMode,
        @SerialName("contact")
        val contact: ContactInfo,
    ) : TimerTask() {
        override val interval: Long get() =
            OffsetDateTime.now().let { it.toNextRank().toEpochSecond() - it.toEpochSecond() }.seconds.toLongMilliseconds()
    }

    @Serializable
    data class Follow(
        @SerialName("last")
        @Serializable(OffsetDateTimeSerializer::class)
        override var last: OffsetDateTime = OffsetDateTime.now(),
        @SerialName("interval")
        override val interval: Long,
        @SerialName("contact")
        val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Recommended(
        @SerialName("last")
        @Serializable(OffsetDateTimeSerializer::class)
        override var last: OffsetDateTime = OffsetDateTime.now(),
        @SerialName("interval")
        override val interval: Long,
        @SerialName("contact")
        val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Backup(
        @SerialName("last")
        @Serializable(OffsetDateTimeSerializer::class)
        override var last: OffsetDateTime = OffsetDateTime.now(),
        @SerialName("interval")
        override val interval: Long,
    ) : TimerTask()
}

private val SEND_DELAY = (1).minutes

internal class TaskLastDelegate(val name: String) : ReadWriteProperty<Nothing?, OffsetDateTime> {

    override fun setValue(thisRef: Nothing?, property: KProperty<*>, value: OffsetDateTime) {
        PixivTaskData.tasks.compute(name) { _, info ->
            when (info) {
                is TimerTask.User -> info.copy(last = value)
                is TimerTask.Rank -> info.copy(last = value)
                is TimerTask.Follow -> info.copy(last = value)
                is TimerTask.Recommended -> info.copy(last = value)
                is TimerTask.Backup -> info.copy(last = value)
                null -> null
            }
        }
    }

    override fun getValue(thisRef: Nothing?, property: KProperty<*>): OffsetDateTime = PixivTaskData.tasks.getValue(name).last
}

internal suspend fun PixivHelper.subscribe(name: String, block: LoadTask) {
    var last : OffsetDateTime by TaskLastDelegate(name)
    block().also {
        addCacheJob(name = "TimerTask(${name})", reply = false) { it }
    }.types(WorkContentType.ILLUST).toList().flatten().toSet().sortedBy { it.createAt }.filter {
        it.createAt > last && it.isR18().not() && it.createAt.isToday()
    }.apply {
        maxOfOrNull { it.createAt }?.let { last = it }
    }.forEach { illust ->
        delay(SEND_DELAY)
        ("Task: $name\n".toPlainText() + buildMessageByIllust(illust = illust, flush = false).toMessageChain()).let {
            send { it }
        }
    }
}

private const val RANK_HOUR = 12

internal fun OffsetDateTime.toNextRank(): OffsetDateTime =
    (if (hour < RANK_HOUR) this else plusDays(1)).withHour(RANK_HOUR).withMinute(0).withSecond(0)

private fun OffsetDateTime.isToday(): Boolean =
    toLocalDate() == LocalDate.now()

private val DELAY_RANDOM = (10..30).random().minutes

internal suspend fun TimerTask.pre(): Unit = when (this) {
    is TimerTask.User -> {
        delay(DELAY_RANDOM)
    }
    is TimerTask.Rank -> {
        delay(DELAY_RANDOM)
    }
    is TimerTask.Follow -> {
        delay(DELAY_RANDOM)
    }
    is TimerTask.Recommended -> {
        delay(DELAY_RANDOM)
    }
    is TimerTask.Backup -> {
        delay(interval)
    }
}

internal suspend fun runTask(name: String, info: TimerTask) = when (info) {
    is TimerTask.User -> {
        info.contact.getHelper().subscribe(name) {
            getUserIllusts(uid = info.uid, limit = 30L) // one page
        }
    }
    is TimerTask.Rank -> {
        info.contact.getHelper().subscribe(name) {
            getRank(mode = info.mode, date = null, limit = 180L).types(WorkContentType.ILLUST)
        }
    }
    is TimerTask.Follow -> {
        info.contact.getHelper().subscribe(name) {
            getFollowIllusts(limit = 90L)
        }
    }
    is TimerTask.Recommended -> {
        info.contact.getHelper().subscribe(name) {
            getRecommended(limit = 90L).eros()
        }
    }
    is TimerTask.Backup -> {
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
}


