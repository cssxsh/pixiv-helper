package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandSender.Companion.asCommandSender
import net.mamoe.mirai.console.command.CommandSender.Companion.asMemberCommandSender
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.command.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.time.*

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

internal val TimerTask.helper
    get() = requireNotNull(findContact(subject)) { "找不到联系人 $subject" }.helper

internal val TimerTask.sender
    get() = when (val contact = requireNotNull(findContact(subject)) { "找不到联系人 $subject" }) {
        is Group -> (contact[user] ?: contact.owner).asMemberCommandSender()
        is User -> contact.asCommandSender(false)
        else -> throw IllegalArgumentException("TimerTask Sender $this")
    }

typealias BuildTask = suspend PixivHelper.() -> Pair<String, TimerTask>

@Serializable
sealed class TimerTask {
    abstract val interval: Long
    abstract val subject: Long
    open val user: Long get() = 12345

    @Serializable
    data class User(
        @SerialName("interval")
        @Contextual
        override val interval: Long,
        @SerialName("uid")
        val uid: Long,
        @SerialName("delegate")
        override val subject: Long,
    ) : TimerTask()

    @Serializable
    data class Rank(
        @SerialName("mode")
        val mode: RankMode,
        @SerialName("delegate")
        override val subject: Long,
    ) : TimerTask() {
        override val interval: Long
            get() = with(OffsetDateTime.now()) { goto(toNextRank()) } * 1000L
    }

    @Serializable
    data class Follow(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("delegate")
        override val subject: Long,
    ) : TimerTask()

    @Serializable
    data class Recommended(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("delegate")
        override val subject: Long,
    ) : TimerTask()

    @Serializable
    data class Backup(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("delegate")
        override val subject: Long,
        @SerialName("user")
        override val user: Long = 12345,
    ) : TimerTask()

    @Serializable
    data class Web(
        @SerialName("interval")
        override val interval: Long,
        @SerialName("delegate")
        override val subject: Long,
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
        override val subject: Long,
        @SerialName("times")
        val times: Int,
    ) : TimerTask()

    @Serializable
    data class Cache(
        @SerialName("delegate")
        override val subject: Long,
        @SerialName("user")
        override val user: Long = 12345,
        @SerialName("arguments")
        val arguments: String
    ) : TimerTask() {
        override val interval: Long
            get() = with(OffsetDateTime.now()) { goto(toNextRank()) } * 1000L
    }
}

private suspend fun PixivHelper.subscribe(name: String, block: LoadTask) {
    val flow = block(name)
    val list = flow.eros(mark = false).notHistory(task = name).onEach { it.write().replicate() }
        .toList().flatten().filter { it.age == AgeLimit.ALL }.distinctBy { it.pid }
    if (isActive.not() || list.isEmpty()) return
    delay(TaskSendInterval * 1000L)
    val nodes = mutableListOf<ForwardMessage.Node>()
    val type = name.substringBefore('(')

    for ((index, illust) in list.sortedBy { it.pid }.withIndex()) {
        if (isActive.not()) break

        val message = buildMessageByIllust(illust = illust)

        if (TaskForward) {
            val sender = (contact as? User) ?: (contact as Group).members.random()
            nodes.add(
                ForwardMessage.Node(
                    senderId = sender.id,
                    senderName = sender.nameCardOrNick,
                    time = illust.createAt.toEpochSecond().toInt(),
                    message = message
                )
            )
        } else {
            delay(TaskSendInterval * 1000L)
            send {
                "Task: $type (${index + 1}/${list.size})\n".toPlainText() + message
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
            RawForwardMessage(nodes).render {
                title = type
                summary = "查看推送的${nodes.size}个作品"
            }
        }
    }
}

private suspend fun PixivHelper.trending(name: String, times: Int = 1) {
    val flow = getTrending(times)
    val list = flow.onEach { it.map(TrendIllust::illust).write().replicate() }.toList().flatten().filter {
        it.illust.isEro(false) && (name to it.illust.pid) !in StatisticTaskInfo
    }
    if (isActive.not() || list.isEmpty()) return
    delay(TaskSendInterval * 1000L)
    val nodes = mutableListOf<ForwardMessage.Node>()
    val type = name.substringBefore('(').substringBefore('[')

    for ((index, trending) in list.withIndex()) {
        if (isActive.not()) break

        val message = buildMessageByIllust(illust = trending.illust)

        if (TaskForward) {
            val sender = (contact as? User) ?: (contact as Group).members.random()
            nodes.add(
                ForwardMessage.Node(
                    senderId = sender.id,
                    senderName = sender.nameCardOrNick,
                    time = trending.illust.createAt.toEpochSecond().toInt(),
                    message = message
                )
            )
        } else {
            delay(TaskSendInterval * 1000L)
            send {
                "Task: $type (${index + 1}/${list.size}) [${trending.tag}]\n".toPlainText() + message
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
            RawForwardMessage(nodes).render {
                title = type
                summary = "查看推送的${nodes.size}个作品"
            }
        }
    }
}

private const val RANK_HOUR = 12

internal fun OffsetDateTime.toNextRank(): OffsetDateTime =
    (if (hour < RANK_HOUR) this else plusDays(1)).withHour(RANK_HOUR).withMinute(0).withSecond(0)

internal fun OffsetDateTime.goto(next: OffsetDateTime) = next.toEpochSecond() - toEpochSecond()

private val RandomMinute get() = (3..10).random() * 60 * 1000L

internal fun TimerTask.pre(): Long {
    return when (this) {
        is TimerTask.User -> RandomMinute
        is TimerTask.Rank -> RandomMinute
        is TimerTask.Follow -> RandomMinute
        is TimerTask.Recommended -> interval
        is TimerTask.Backup -> interval
        is TimerTask.Web -> RandomMinute
        is TimerTask.Trending -> interval
        is TimerTask.Cache -> interval
    }
}

internal suspend fun TimerTask.run(name: String) {
    when (this) {
        is TimerTask.User -> {
            helper.subscribe(name) {
                getUserIllusts(detail = userDetail(uid = this@run.uid), limit = PAGE_SIZE).isToday()
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
            with(PixivBackupCommand) {
                sender.data()
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
        is TimerTask.Cache -> {
            @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
            try {
                val result = PixivCacheCommand.execute(sender = sender, arguments = arguments, checkPermission = false)
                if (result.isFailure()) {
                    logger.warning { "Task Cache 执行错误 $result" }
                } else {
                    delay(3_000)
                }
            } catch (cause: Throwable) {
                logger.warning { "Task Cache 执行错误 $cause" }
            }
        }
    }
}


