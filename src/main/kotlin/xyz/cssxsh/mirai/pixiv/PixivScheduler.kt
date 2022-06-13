package xyz.cssxsh.mirai.pixiv

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.CommandSender.Companion.asCommandSender
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.mirai.pixiv.task.*
import java.time.*
import kotlin.coroutines.*

public object PixivScheduler : CoroutineScope {
    private val logger by lazy { MiraiLogger.Factory.create(this::class, identity = "pixiv-scheduler") }

    override val coroutineContext: CoroutineContext =
        CoroutineName(name = "pixiv-scheduler") + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
            logger.warning({ "$throwable in $context" }, throwable)
        }

    private val tasks by PixivTaskData::tasks

    private val jobs: MutableMap<String, Job> = HashMap()

    /**
     * 运行任务
     */
    private fun run(task: PixivTimerTask): Job = launch {
        when (task) {
            is PixivTimerTask.Cache -> {
                if (task.subject != null) {
                    task.withHelper {

                    }
                } else {
                    TODO()
                }
            }
            is PixivTimerTask.Follow -> task.withHelper { /* TODO */ }
            is PixivTimerTask.Rank -> task.withHelper { /* TODO */ }
            is PixivTimerTask.Recommended -> task.withHelper { /* TODO */ }
            is PixivTimerTask.Trending -> task.withHelper { /* TODO */ }
            is PixivTimerTask.User -> task.withHelper { /* TODO */ }
        }
    }

    /**
     * 设置任务
     */
    public operator fun set(id: String, task: PixivTimerTask) {
        if (id in tasks) throw IllegalArgumentException("$id already exists")
        tasks[id] = task
        jobs[id] = launch {
            while (isActive) {
                val millis = task.cron.toExecutionTime()
                    .timeToNextExecution(ZonedDateTime.now())
                    .orElse(Duration.ZERO)
                    .toMillis()
                if (millis <= 0) {
                    logger.info { "任务 ${task} 结束" }
                    break
                }
                run(task = task)
            }
        }
    }

    /**
     * 移除任务
     */
    public fun remove(id: String): PixivTimerTask? {
        val task = tasks.remove(id)
        jobs.remove(id)?.cancel("任务被终止")

        return task
    }

    private suspend fun PixivTimerTask.withHelper(block: suspend PixivHelper.() -> Any?) {
        val contact = findContact(subject!!) ?: throw NoSuchElementException("$subject")
        val sender = when (contact) {
            is User -> contact.asCommandSender(false)
            is Group -> contact.getOrFail(id = user!!).asCommandSender(false)
            else -> throw IllegalArgumentException("sender")
        }

        sender.withHelper(block)
    }
}

//internal data class CacheTask(
//    val name: String,
//    val write: Boolean,
//    val reply: Boolean,
//    val block: LoadTask,
//)
//
//internal data class DownloadTask(
//    val name: String,
//    val list: List<IllustInfo>,
//    val reply: Boolean,
//)
//
//internal val TimerTask.helper
//    get() = requireNotNull(findContact(subject)) { "找不到联系人 $subject" }.helper
//
//internal val TimerTask.sender
//    get() = when (val contact = requireNotNull(findContact(subject)) { "找不到联系人 $subject" }) {
//        is Group -> (contact[user] ?: contact.owner).asMemberCommandSender()
//        is User -> contact.asCommandSender(false)
//        else -> throw IllegalArgumentException("TimerTask Sender $this")
//    }
//
//typealias BuildTask = suspend PixivHelper.() -> Pair<String, TimerTask>
//

//
//private suspend fun PixivHelper.subscribe(name: String, block: LoadTask) {
//    val flow = block(name)
//    val list = flow.eros(mark = false).notHistory(task = name).onEach { it.write().replicate() }
//        .toList().flatten().filter { it.age == AgeLimit.ALL }.distinctBy { it.pid }
//    if (isActive.not() || list.isEmpty()) return
//    delay(TaskSendInterval * 1000L)
//    val nodes = mutableListOf<ForwardMessage.Node>()
//    val type = name.substringBefore('(')
//
//    for ((index, illust) in list.sortedBy { it.pid }.withIndex()) {
//        if (isActive.not()) break
//
//        val message = buildMessageByIllust(illust = illust)
//
//        if (TaskForward) {
//            val sender = (contact as? User) ?: (contact as Group).members.random()
//            nodes.add(
//                ForwardMessage.Node(
//                    senderId = sender.id,
//                    senderName = sender.nameCardOrNick,
//                    time = illust.createAt.toEpochSecond().toInt(),
//                    message = message
//                )
//            )
//        } else {
//            delay(TaskSendInterval * 1000L)
//            send {
//                "Task: $type (${index + 1}/${list.size})\n".toPlainText() + message
//            }
//        }
//
//        StatisticTaskInfo(
//            task = name,
//            pid = illust.pid,
//            timestamp = OffsetDateTime.now().toEpochSecond()
//        ).replicate()
//    }
//
//    if (TaskForward) {
//        send {
//            RawForwardMessage(nodes).render {
//                title = type
//                summary = "查看推送的${nodes.size}个作品"
//            }
//        }
//    }
//}
//
//private suspend fun PixivHelper.trending(name: String, times: Int = 1) {
//    val flow = getTrending(times)
//    val list = flow.onEach { it.map(TrendIllust::illust).write().replicate() }.toList().flatten().filter {
//        it.illust.isEro(false) && (name to it.illust.pid) !in StatisticTaskInfo
//    }
//    if (isActive.not() || list.isEmpty()) return
//    delay(TaskSendInterval * 1000L)
//    val nodes = mutableListOf<ForwardMessage.Node>()
//    val type = name.substringBefore('(').substringBefore('[')
//
//    for ((index, trending) in list.withIndex()) {
//        if (isActive.not()) break
//
//        val message = buildMessageByIllust(illust = trending.illust)
//
//        if (TaskForward) {
//            val sender = (contact as? User) ?: (contact as Group).members.random()
//            nodes.add(
//                ForwardMessage.Node(
//                    senderId = sender.id,
//                    senderName = sender.nameCardOrNick,
//                    time = trending.illust.createAt.toEpochSecond().toInt(),
//                    message = message
//                )
//            )
//        } else {
//            delay(TaskSendInterval * 1000L)
//            send {
//                "Task: $type (${index + 1}/${list.size}) [${trending.tag}]\n".toPlainText() + message
//            }
//        }
//        StatisticTaskInfo(
//            task = name,
//            pid = trending.illust.pid,
//            timestamp = OffsetDateTime.now().toEpochSecond()
//        ).replicate()
//    }
//
//    if (TaskForward) {
//        send {
//            RawForwardMessage(nodes).render {
//                title = type
//                summary = "查看推送的${nodes.size}个作品"
//            }
//        }
//    }
//}
//
//private const val RANK_HOUR = 12
//
//internal fun OffsetDateTime.toNextRank(): OffsetDateTime =
//    (if (hour < RANK_HOUR) this else plusDays(1)).withHour(RANK_HOUR).withMinute(0).withSecond(0)
//
//internal fun OffsetDateTime.goto(next: OffsetDateTime) = next.toEpochSecond() - toEpochSecond()
//
//private val RandomMinute get() = (3..10).random() * 60 * 1000L
//
//internal fun TimerTask.pre(): Long {
//    return when (this) {
//        is TimerTask.User -> RandomMinute
//        is TimerTask.Rank -> RandomMinute
//        is TimerTask.Follow -> RandomMinute
//        is TimerTask.Recommended -> interval
//        is TimerTask.Backup -> interval
//        is TimerTask.Web -> RandomMinute
//        is TimerTask.Trending -> interval
//        is TimerTask.Cache -> interval
//    }
//}
//
//internal suspend fun TimerTask.run(name: String) {
//    when (this) {
//        is TimerTask.User -> {
//            helper.subscribe(name) {
//                getUserIllusts(detail = userDetail(uid = this@run.uid), limit = PAGE_SIZE).isToday()
//            }
//        }
//        is TimerTask.Rank -> {
//            helper.subscribe(name) {
//                getRank(mode = mode).eros()
//            }
//        }
//        is TimerTask.Follow -> {
//            helper.subscribe(name) {
//                getFollowIllusts(limit = TASK_LOAD).isToday()
//            }
//        }
//        is TimerTask.Recommended -> {
//            helper.subscribe(name) {
//                getRecommended(limit = PAGE_SIZE).eros()
//            }
//        }
//        is TimerTask.Backup -> {
//            with(PixivBackupCommand) {
//                sender.data()
//            }
//        }
//        is TimerTask.Web -> {
//            helper.subscribe(name) {
//                getListIllusts(set = loadWeb(url = Url(url), regex = pattern.toRegex()))
//            }
//        }
//        is TimerTask.Trending -> {
//            helper.trending(name = name, times = times)
//        }
//        is TimerTask.Cache -> {
//            @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
//            try {
//                val result = PixivCacheCommand.execute(sender = sender, arguments = arguments, checkPermission = false)
//                if (result.isFailure()) {
//                    logger.warning { "Task Cache 执行错误 $result" }
//                } else {
//                    delay(3_000)
//                }
//            } catch (cause: Throwable) {
//                logger.warning { "Task Cache 执行错误 $cause" }
//            }
//        }
//    }
//}