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
import net.mamoe.mirai.message.data.toPlainText
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsFile
import xyz.cssxsh.baidu.getRapidUploadInfo
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
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

@Serializable
sealed class TimerTask {
    abstract var last: OffsetDateTime
    abstract val interval: Long
    abstract val contact: ContactInfo

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
        override val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Rank(
        @SerialName("last")
        @Serializable(OffsetDateTimeSerializer::class)
        override var last: OffsetDateTime = OffsetDateTime.now(),
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
        @SerialName("last")
        @Serializable(OffsetDateTimeSerializer::class)
        override var last: OffsetDateTime = OffsetDateTime.now(),
        @SerialName("interval")
        override val interval: Long,
        @SerialName("contact")
        override val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Recommended(
        @SerialName("last")
        @Serializable(OffsetDateTimeSerializer::class)
        override var last: OffsetDateTime = OffsetDateTime.now(),
        @SerialName("interval")
        override val interval: Long,
        @SerialName("contact")
        override val contact: ContactInfo,
    ) : TimerTask()

    @Serializable
    data class Backup(
        @SerialName("last")
        @Serializable(OffsetDateTimeSerializer::class)
        override var last: OffsetDateTime = OffsetDateTime.now(),
        @SerialName("interval")
        override val interval: Long,
        @SerialName("contact")
        override val contact: ContactInfo,
    ) : TimerTask()
}

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

    override fun getValue(thisRef: Nothing?, property: KProperty<*>): OffsetDateTime =
        PixivTaskData.tasks.getValue(name).last
}

internal suspend fun PixivHelper.subscribe(name: String, block: LoadTask) {
    var last: OffsetDateTime by TaskLastDelegate(name)
    block().also {
        addCacheJob(name = "TimerTask(${name})", reply = false) { it }
    }.types(WorkContentType.ILLUST).toList().flatten().toSet().sortedBy { it.createAt }.filter {
        it.createAt > last && it.age == AgeLimit.ALL && it.createAt.isToday()
    }.apply {
        maxOfOrNull { it.createAt }?.let { last = it }
    }.forEach { illust ->
        delay(interval)
        send {
            "Task: $name\n".toPlainText() + buildMessageByIllust(illust = illust)
        }
    }
}

private const val RANK_HOUR = 12

internal fun OffsetDateTime.toNextRank(): OffsetDateTime =
    (if (hour < RANK_HOUR) this else plusDays(1)).withHour(RANK_HOUR).withMinute(0).withSecond(0)

internal fun OffsetDateTime.goto(next: OffsetDateTime) = (next.toEpochSecond() - toEpochSecond()).seconds

private fun OffsetDateTime.isToday(): Boolean = (toLocalDate() == LocalDate.now())

private val random = (10..30).random().minutes

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
        delay(random)
    }
    is TimerTask.Backup -> {
        delay(interval)
    }
}

internal suspend fun runTask(name: String, info: TimerTask) = when (info) {
    is TimerTask.User -> {
        info.contact.helper.subscribe(name) {
            getUserIllusts(detail = userDetail(uid = info.uid), limit = PAGE_SIZE)
        }
    }
    is TimerTask.Rank -> {
        info.contact.helper.subscribe(name) {
            getRank(mode = info.mode, limit = TASK_LOAD).types(WorkContentType.ILLUST)
        }
    }
    is TimerTask.Follow -> {
        info.contact.helper.subscribe(name) {
            getFollowIllusts(limit = TASK_LOAD)
        }
    }
    is TimerTask.Recommended -> {
        info.contact.helper.subscribe(name) {
            getRecommended(limit = TASK_LOAD).eros()
        }
    }
    is TimerTask.Backup -> {
        val helper = info.contact.helper
        PixivZipper.compressData(list = getBackupList()).forEach { file ->
            if (helper.contact is FileSupported) {
                helper.contact.sendMessage("${file.name} 压缩完毕，开始上传到群文件")
                runCatching {
                    helper.contact.sendMessage(file.toExternalResource().uploadAsFile(contact = helper.contact, path = file.name))
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
}


