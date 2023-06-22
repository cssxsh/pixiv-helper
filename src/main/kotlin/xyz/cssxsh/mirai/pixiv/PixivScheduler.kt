package xyz.cssxsh.mirai.pixiv

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
import xyz.cssxsh.pixiv.*
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

    private fun record(id: String, illust: IllustInfo) {
        StatisticTaskInfo(
            task = id,
            pid = illust.pid,
            timestamp = OffsetDateTime.now().toEpochSecond()
        ).persist()
    }

    private suspend fun UserCommandSender.push(id: String, name: String, task: PixivTimerTask, count: Int) {
        val nodes = mutableListOf<ForwardMessage.Node>()
        var index = 0

        while (index++ < count) {
            val illust = task.push() ?: break

            val message = try {
                buildIllustMessage(illust = illust, contact = subject)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                logger.warning({ "push ${illust.pid} fail." }, cause)
                continue
            }

            if (TaskForward) {
                val sender = (subject as? User) ?: (subject as Group).members.random()
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
                sendMessage("Task: $name (${index}/${count})\n".toPlainText() + message)
            }

            launch {
                record(id, illust)
            }
        }

        if (TaskForward && nodes.isNotEmpty()) {
            sendMessage(RawForwardMessage(nodes).render {
                title = name
                summary = "查看推送的${nodes.size}个作品"
            })
        }
    }

    private fun PixivTimerTask.push(): IllustInfo? {
        while (illusts.isNotEmpty()) {
            val illust = illusts.removeFirstOrNull() ?: break
            if (illust.age != AgeLimit.ALL) continue
            if ((id to illust.pid) in StatisticTaskInfo) continue

            return illust
        }
        return null
    }

    /**
     * 准备任务
     */
    private fun prepare(task: PixivTimerTask) {
        launch(CoroutineName(name = task.id)) {
            task.subscribe {
                when (task) {
                    is PixivTimerTask.Cache -> {}
                    is PixivTimerTask.Follow -> {
                        if (task.illusts.isEmpty() && task.mutex.tryLock()) {
                            val client = client()
                            PixivCacheLoader.cache(task = buildPixivCacheTask {
                                name = task.id
                                flow = client.follow().onEach { page ->
                                    for (illust in page) {
                                        if (illust.type != WorkContentType.MANGA) {
                                            task.illusts.add(illust)
                                        }
                                    }
                                }
                            }) { _, _ ->
                                task.mutex.unlock()
                            }
                        }
                    }
                    is PixivTimerTask.Rank -> {
                        if (task.illusts.isEmpty() && task.mutex.tryLock()) {
                            val client = client()
                            PixivCacheLoader.cache(task = buildPixivCacheTask {
                                name = task.id
                                flow = client.rank(task.mode).onEach { page ->
                                    for (illust in page) {
                                        if (illust.type != WorkContentType.MANGA) {
                                            task.illusts.add(illust)
                                        }
                                    }
                                }
                            }) { _, _ ->
                                task.mutex.unlock()
                            }
                        }
                    }
                    is PixivTimerTask.Recommended -> {
                        if (task.illusts.isEmpty() && task.mutex.tryLock()) {
                            val client = client()
                            PixivCacheLoader.cache(task = buildPixivCacheTask {
                                name = task.id
                                flow = client.recommended().onEach { page ->
                                    for (illust in page) {
                                        if (illust.type != WorkContentType.MANGA) {
                                            task.illusts.add(illust)
                                        }
                                    }
                                }
                            }) { _, _ ->
                                task.mutex.unlock()
                            }
                        }
                    }
                    is PixivTimerTask.Trending -> {
                        // TODO PixivTimerTask.Trending
                    }
                    is PixivTimerTask.User -> {
                        if (task.illusts.isEmpty() && task.mutex.tryLock()) {
                            val client = client()
                            val detail = client.userDetail(uid = task.uid)
                            PixivCacheLoader.cache(task = buildPixivCacheTask {
                                name = task.id
                                flow = client.user(detail).onEach { page ->
                                    for (illust in page) {
                                        if (illust.type != WorkContentType.MANGA) {
                                            task.illusts.add(illust)
                                        }
                                    }
                                }
                            }) { _, _ ->
                                task.mutex.unlock()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 运行任务
     */
    @Suppress("EXPERIMENTAL_API_USAGE")
    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    private fun run(task: PixivTimerTask) {
        launch(CoroutineName(name = task.id)) {
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
                        push(id = task.id, name = "Follow", task = task, count = TaskConut)
                    }
                    is PixivTimerTask.Rank -> {
                        push(id = task.id, name = "Rank", task = task, count = TaskConut)
                    }
                    is PixivTimerTask.Recommended -> {
                        push(id = task.id, name = "Recommended", task = task, count = TaskConut)
                    }
                    is PixivTimerTask.Trending -> {
                        // TODO PixivTimerTask.Trending
                    }
                    is PixivTimerTask.User -> {
                        push(id = task.id, name = "User", task = task, count = TaskConut)
                    }
                }
            }
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