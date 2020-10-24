package mirai.command

import com.soywiz.klock.wrapped.WDateTime
import kotlinx.coroutines.*
import mirai.data.BilibiliTaskData
import mirai.tools.BiliVideo
import net.mamoe.mirai.console.command.CommandOwner
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionId
import net.mamoe.mirai.console.permission.RootPermission
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.utils.minutesToMillis
import kotlin.coroutines.CoroutineContext

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

    override val coroutineContext: CoroutineContext = CoroutineName("Bilibili-Listener")

    private val videoJobs = mutableMapOf<Long, Job>()

    private val videoContact = mutableMapOf<Long, List<Contact>>()

    private fun addVideoListener(uid: Long, delayTime: Long = (10).minutesToMillis): Job = launch {
        while (isActive) {
            BiliVideo.searchVideo(uid).result.list.vList.filter {
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
                        appendLine("链接: https://www.bilibili.com/video/${video.bVid}")
                    }.let { info ->
                        videoContact.getValue(uid).forEach { contact ->
                            contact.runCatching {
                                sendMessage(info)
                            }
                        }
                    }
                }
            }
            delay(delayTime)
        }
    }

    @SubCommand("video", "视频")
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.video(uid: Long) = runCatching {
        videoContact.compute(uid) { _, list ->
            (list ?: emptyList()) + fromEvent.subject
        }
        videoJobs.compute(uid) { _, job ->
            job?.takeIf { it.isActive } ?: addVideoListener(uid)
        }
    }.onSuccess { job ->
        quoteReply("添加对${uid}的监听任务完成${job}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}