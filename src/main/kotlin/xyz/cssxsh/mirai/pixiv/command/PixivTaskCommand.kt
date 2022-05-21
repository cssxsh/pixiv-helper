package xyz.cssxsh.mirai.pixiv.command

import io.ktor.http.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.pixiv.*
import java.time.*

object PixivTaskCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "task",
    description = "PIXIV定时器",
    overrideContext = PixivCommandArgumentContext
), PixivHelperCommand {
    private const val TASK_DURATION = 3 * 60

    private const val MINUTE = 60 * 1000L

    private suspend fun UserCommandSender.task(block: BuildTask) = withHelper {
        val (name, task) = block()
        PixivHelperScheduler.setTimerTask(name = name, info = task)
        "定时任务${name}已添加，间隔${task.interval / MINUTE}min"
    }

    @SubCommand
    @Description("推送用户新作品")
    suspend fun UserCommandSender.user(uid: Long, minute: Int = TASK_DURATION) = task {
        "User($uid)[${contact}]" to
            TimerTask.User(uid = uid, interval = minute * MINUTE, subject = contact.delegate)
    }

    @SubCommand
    @Description("推送排行榜新作品")
    suspend fun UserCommandSender.rank(mode: RankMode) = task {
        "Rank<$mode>[${contact}]" to
            TimerTask.Rank(mode = mode, subject = contact.delegate)
    }

    @SubCommand
    @Description("推送关注用户作品")
    suspend fun UserCommandSender.follow(minute: Int = TASK_DURATION) = task {
        "Follow(${info().user.uid})[${contact}]" to
            TimerTask.Follow(interval = minute * MINUTE, subject = contact.delegate)
    }

    @SubCommand
    @Description("推送推荐作品")
    suspend fun UserCommandSender.recommended(minute: Int = TASK_DURATION) = task {
        "Recommended(${info().user.uid})[${contact}]" to
            TimerTask.Recommended(interval = minute * MINUTE, subject = contact.delegate)
    }

    @SubCommand
    @Description("定时备份任务")
    suspend fun UserCommandSender.backup(minute: Int = TASK_DURATION) = task {
        "Backup" to
            TimerTask.Backup(interval = minute * MINUTE, subject = contact.delegate)
    }

    @SubCommand
    @Description("定时缓存任务")
    suspend fun UserCommandSender.cache(vararg args: String) = task {
        "Cache(${args.joinToString()})[${contact}]" to
            TimerTask.Cache(subject = contact.delegate, user = user.id, arguments = args.joinToString(separator = " "))
    }

    @SubCommand
    @Description("推送，从url链接获取")
    suspend fun UserCommandSender.web(pattern: String, link: String, minute: Int = TASK_DURATION) = task {
        val url = Url(link)
        val set = loadWeb(url = url, regex = pattern.toRegex()).ifEmpty {
            throw IllegalStateException("来自${url}加载的作品ID应该不为空")
        }

        sendMessage("来自${url}加载得到${set}，定时任务将添加")
        "WEB(${url.host})<${pattern}>[${contact}]" to TimerTask.Web(
            interval = minute * MINUTE,
            subject = contact.delegate,
            url = link,
            pattern = pattern
        )
    }

    @SubCommand
    @Description("推送热门标签")
    suspend fun UserCommandSender.trending(minute: Int = TASK_DURATION, times: Int = 1) = task {
        "Trending[${contact}]" to
            TimerTask.Trending(interval = minute * MINUTE, subject = contact.delegate, times = times)
    }

    @SubCommand
    @Description("定时任务，删除")
    suspend fun CommandSender.delete(vararg args: String) {
        val name = args.joinToString(separator = " ")
        val message = try {
            PixivHelperScheduler.removeTimerTask(name)
            "定时任务${name}已删除"
        } catch (cause: Throwable) {
            "定时任务${name}删除失败，${cause.message}"
        }

        sendMessage(message)
    }

    @SubCommand
    @Description("查看任务详情")
    suspend fun CommandSender.detail() {
        sendMessage(
            message = buildMessageChain {
                for ((name, task) in PixivTaskData.tasks) {
                    appendLine("> ---------")
                    appendLine("名称: $name , 间隔: ${task.interval / MINUTE}min")
                    try {
                        with(StatisticTaskInfo.last(name) ?: continue) {
                            val time =
                                OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
                            appendLine("最后播放作品ID $pid 时间 $time")
                        }
                    } catch (cause: Throwable) {
                        appendLine("最后播放作品ID 查询错误 ${cause.findSQLException() ?: cause}")
                    }
                }
            }.ifEmpty {
                "任务为空".toPlainText()
            }
        )
    }
}