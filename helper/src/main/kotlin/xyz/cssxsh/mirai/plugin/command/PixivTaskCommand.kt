package xyz.cssxsh.mirai.plugin.command

import io.ktor.http.*
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.data.buildMessageChain
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivTaskData
import xyz.cssxsh.pixiv.*
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.*

object PixivTaskCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "task",
    description = "PIXIV定时器",
    overrideContext = PixivCommandArgumentContext
) {
    private const val TASK_DURATION = 3 * 60

    private suspend fun CommandSenderOnMessage<*>.setTask(block: BuildTask) = withHelper {
        val (name, task) = block()
        PixivHelperScheduler.setTimerTask(name = name, info = task)
        "定时任务${name}已添加，间隔${task.interval.milliseconds}"
    }

    @SubCommand
    @Description("设置用户定时订阅任务")
    suspend fun CommandSenderOnMessage<*>.user(uid: Long, duration: Int = TASK_DURATION) = setTask {
        "User($uid)[${contact}]" to
            TimerTask.User(uid = uid, interval = duration.minutes.toLongMilliseconds(), contact = getContactInfo())
    }

    @SubCommand
    @Description("设置排行榜定时订阅任务")
    suspend fun CommandSenderOnMessage<*>.rank(mode: RankMode) = setTask {
        "Rank($mode)[${contact}]" to TimerTask.Rank(mode = mode, contact = getContactInfo())
    }

    @SubCommand
    @Description("设置关注推送定时订阅任务")
    suspend fun CommandSenderOnMessage<*>.follow(duration: Int = TASK_DURATION) = setTask {
        "Follow(${getAuthInfo().user.uid})[${contact}]" to
            TimerTask.Follow(interval = duration.minutes.toLongMilliseconds(), contact = getContactInfo())
    }

    @SubCommand
    @Description("设置推荐画师定时订阅任务")
    suspend fun CommandSenderOnMessage<*>.recommended(duration: Int = TASK_DURATION) = setTask {
        "Recommended(${getAuthInfo().user.uid})[${contact}]" to
            TimerTask.Recommended(interval = duration.minutes.toLongMilliseconds(), contact = getContactInfo())
    }

    @SubCommand
    @Description("设置定时备份任务")
    suspend fun CommandSenderOnMessage<*>.backup(duration: Int = TASK_DURATION) = setTask {
        "Backup" to TimerTask.Backup(interval = duration.minutes.toLongMilliseconds(), contact = getContactInfo())
    }

    @SubCommand
    @Description("设置定时备份任务")
    suspend fun CommandSenderOnMessage<*>.web(pattern: String, link: String, duration: Int = TASK_DURATION) = setTask {
        val url = Url(link)
        loadWeb(url = Url(link), regex = pattern.toRegex()).let {
            check(it.isNotEmpty()) { "来自${url}加载的作品ID应该不为空" }
            sendMessage("来自${url}加载得到${it}，定时任务将添加")
        }
        "WEB(${url.host})<${pattern}>[${contact}]" to TimerTask.Web(
            interval = duration.minutes.toLongMilliseconds(),
            contact = getContactInfo(),
            url = link,
            pattern = pattern
        )
    }

    private fun url(uid: Long) = Url(
        urlString = "https://m.weibo.cn/api/container/getIndex?value=${uid}&containerid=1076037267021686"
    )

    private val regex = """(?<=Pixiv.{1,32})\d{8}""".toRegex()

    @SubCommand
    @Description("设置定时备份任务")
    suspend fun CommandSenderOnMessage<*>.weibo(uid: Long, duration: Int = TASK_DURATION) = setTask {
        val url: Url = url(uid)
        loadWeb(url = url, regex = regex).let {
            check(it.isNotEmpty()) { "来自${url}加载的作品ID应该不为空" }
            sendMessage("来自${url}加载得到${it}，定时任务将添加")
        }
        "WEIBO($uid)[${contact}]" to TimerTask.Web(
            interval = duration.minutes.toLongMilliseconds(),
            contact = getContactInfo(),
            url = url.toString(),
            pattern = regex.pattern
        )
    }

    @SubCommand
    @Description("查看任务详情")
    suspend fun CommandSenderOnMessage<*>.detail() = withHelper {
        buildMessageChain {
            PixivTaskData.tasks.forEach { (name, info) ->
                appendLine("名称: $name , 间隔: ${info.interval.milliseconds}")
                useMappers { it.statistic.histories(name = name) }.maxByOrNull { it.timestamp }?.let {
                    val time = Instant.ofEpochSecond(it.timestamp).atOffset(ZoneOffset.UTC)
                    appendLine("最后播放作品ID ${it.pid} 时间 $time")
                }
            }
        }
    }
}