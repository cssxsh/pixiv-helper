package xyz.cssxsh.mirai.pixiv

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandSender.Companion.asCommandSender
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.command.*
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.mirai.pixiv.task.*
import xyz.cssxsh.pixiv.AgeLimit
import xyz.cssxsh.pixiv.apps.*
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
    @Suppress("EXPERIMENTAL_API_USAGE")
    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    private fun run(task: PixivTimerTask) {
        launch {
            task.subscribe {
                when (task) {
                    is PixivTimerTask.Cache -> {
                        val result = PixivCacheCommand.execute(
                            sender = this,
                            arguments = task.arguments,
                            checkPermission = false
                        )
                        if (result.isFailure()) {
                            logger.warning { "Task Cache 执行错误 $result" }
                        }
                    }
                    is PixivTimerTask.Follow -> {
                        push(id = task.id, name = "Follow", list = task.take(TaskConut))
                    }
                    is PixivTimerTask.Rank -> {
                        push(id = task.id, name = "Rank", list = task.take(TaskConut))
                    }
                    is PixivTimerTask.Recommended -> {
                        push(id = task.id, name = "Recommended", list = task.take(TaskConut))
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
    public operator fun plusAssign(task: PixivTimerTask) {
        if (task.id in tasks) throw IllegalArgumentException("${task.id} already exists")
        tasks[task.id] = task
        jobs[task.id] = launch {
            while (isActive) {
                prepare(task = task)
                val millis = task.cron.toExecutionTime()
                    .timeToNextExecution(ZonedDateTime.now())
                    .orElse(Duration.ZERO)
                    .toMillis()
                delay(millis)
                run(task = task)
                delay(TaskSendInterval * 1000L)
            }
        }
    }

    /**
     * 移除任务
     */
    public operator fun get(id: String): PixivTimerTask? {
        return tasks[id]
    }

    /**
     * 移除任务
     */
    public fun remove(id: String): PixivTimerTask? {
        val task = tasks.remove(id)
        jobs.remove(id)?.cancel("任务被终止")

        return task
    }

    private suspend fun PixivTimerTask.subscribe(block: suspend UserCommandSender.() -> Unit) {
        val contact = findContact(subject) ?: throw NoSuchElementException("$subject")
        val sender = when (contact) {
            is User -> contact.asCommandSender(false)
            is Group -> contact.getOrFail(id = user ?: contact.bot.id).asCommandSender(false)
            else -> throw IllegalArgumentException("$contact")
        }

        sender.block()
    }

    public fun detail(): String {
        return tasks.entries.joinToString("\n") { (id, task) ->
            """$id | "${task.cron}" | ${task.illusts.size}"""
        }
    }

    public fun start() {
        for ((id, task) in tasks) {
            if (jobs[id]?.isActive == true) continue
            jobs[id] = launch {
                while (isActive) {
                    prepare(task = task)
                    val millis = task.cron.toExecutionTime()
                        .timeToNextExecution(ZonedDateTime.now())
                        .orElse(Duration.ZERO)
                        .toMillis()
                    delay(millis)
                    run(task = task)
                    delay(TaskSendInterval * 1000L)
                }
            }
        }
    }

    public fun stop() {
        jobs.forEach { (_, job) ->
            job.cancel()
        }
    }
}

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