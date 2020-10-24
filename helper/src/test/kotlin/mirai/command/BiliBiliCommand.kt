package mirai.command

import com.soywiz.klock.wrapped.WDateTime
import kotlinx.coroutines.*
import mirai.data.BilibiliTaskData
import mirai.tools.Bilibili
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.command.CommandOwner
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionId
import net.mamoe.mirai.console.permission.RootPermission
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.sendImage
import net.mamoe.mirai.utils.hoursToMillis
import net.mamoe.mirai.utils.minutesToMillis
import kotlin.coroutines.CoroutineContext

@ConsoleExperimentalApi
object BiliBiliCommand : CompositeCommand(
    owner = object : CommandOwner {
        override val parentPermission: Permission
            get() = RootPermission

        override fun permissionId(name: String): PermissionId =
            PermissionId("bilibili", name)
    },
    "bilibili", "B站",
    description = "缓存指令",
    prefixOptional = true
), CoroutineScope {

    private val logger by lazy {
        MiraiConsole.createLogger("bilibili")
    }

    override val coroutineContext: CoroutineContext = CoroutineName("Bilibili-Listener")

    private val delayIntervalMillis = 3.minutesToMillis..1.hoursToMillis

    private val videoJobs = mutableMapOf<Long, Job>()

    private val liveJobs = mutableMapOf<Long, Job>()

    private val videoContact = mutableMapOf<Long, Set<Contact>>()

    private val liveContact = mutableMapOf<Long, Set<Contact>>()

    private fun addVideoListener(uid: Long): Job = launch {
        while (isActive) {
            runCatching {
                Bilibili.searchVideo(uid).searchData.list.vList.apply {
                    maxByOrNull { it.created }?.let { video ->
                        logger.verbose("(${uid})最新视频为${video}")
                    }
                }.filter {
                    it.created >= BilibiliTaskData.video.getOrPut(uid) { WDateTime.nowUnixLong() }
                }.apply {
                    maxByOrNull { it.created }?.let { video ->
                        BilibiliTaskData.video[uid] = video.created
                    }
                    forEach { video ->
                        buildString {
                            appendLine("标题: ${video.title}")
                            appendLine("作者: ${video.author}")
                            appendLine("时长: ${video.length}")
                            appendLine("链接: https://www.bilibili.com/video/${video.bvId}")
                        }.let { info ->
                            videoContact.getValue(uid).forEach { contact ->
                                contact.runCatching {
                                    sendMessage(info)
                                    sendImage(Bilibili.getPic(video.pic).inputStream())
                                }
                            }
                        }
                    }
                }
            }.onSuccess { list ->
                (if (list.isEmpty()) delayIntervalMillis.first else delayIntervalMillis.last).let {
                    logger.verbose("(${uid})视频监听任务完成一次, 目前时间戳为${BilibiliTaskData.video[uid]}, 共有${list.size}个视频更新, 即将进入延时delay(${it}ms)。")
                    delay(it)
                }
            }.onFailure {
                logger.warning("(${uid})视频监听任务执行失败", it)
                delay(delayIntervalMillis.first)
            }
        }
    }

    private fun addLiveListener(uid: Long): Job = launch {
        BilibiliTaskData.live[uid] = false
        while (isActive) {
            runCatching {
                Bilibili.accInfo(uid).userData.also { user ->
                    logger.verbose("(${uid})最新直播间状态为${user.liveRoom}")
                    BilibiliTaskData.live.put(uid, user.liveRoom.liveStatus == 1).let {
                        if (it != true && user.liveRoom.liveStatus == 1) {
                            buildString {
                                appendLine("主播: ${user.name}")
                                appendLine("标题: ${user.liveRoom.title}")
                                appendLine("人气: ${user.liveRoom.online}")
                                appendLine("链接: ${user.liveRoom.url}")
                            }.let { info ->
                                liveContact.getValue(uid).forEach { contact ->
                                    contact.runCatching {
                                        sendMessage(info)
                                        sendImage(Bilibili.getPic(user.liveRoom.cover).inputStream())
                                    }
                                }
                            }
                        }
                    }
                }
            }.onSuccess { user ->
                (if (user.liveRoom.liveStatus == 0) delayIntervalMillis.first else delayIntervalMillis.last).let {
                    logger.verbose("(${uid})直播监听任务完成一次, 目前直播状态为${user.liveRoom.liveStatus}, 即将进入延时delay(${it}ms)。")
                    delay(it)
                }
            }.onFailure {
                logger.warning("(${uid})直播监听任务执行失败", it)
            }
        }
    }

    @SubCommand("video", "视频")
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.video(uid: Long) = runCatching {
        videoContact.compute(uid) { _, list ->
            (list ?: emptySet()) + fromEvent.subject
        }
        videoJobs.compute(uid) { _, job ->
            job?.takeIf { it.isActive } ?: addVideoListener(uid)
        }
    }.onSuccess { job ->
        quoteReply("添加对${uid}的视频监听任务, 添加完成${job}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    @SubCommand("live", "直播")
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.live(uid: Long) = runCatching {
        liveContact.compute(uid) { _, list ->
            (list ?: emptySet()) + fromEvent.subject
        }
        liveJobs.compute(uid) { _, job ->
            job?.takeIf { it.isActive } ?: addLiveListener(uid)
        }
    }.onSuccess { job ->
        quoteReply("添加对${uid}的直播监听任务, 添加完成${job}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}