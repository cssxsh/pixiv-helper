package xyz.cssxsh.mirai.pixiv.command

import com.cronutils.model.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.task.*
import xyz.cssxsh.pixiv.*

public object PixivTaskCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "task",
    description = "PIXIV定时器",
    overrideContext = PixivCommandArgumentContext
), PixivHelperCommand {

    @SubCommand
    @Description("推送用户新作品")
    public suspend fun CommandSender.user(cron: Cron, target: Contact? = subject) {
        val subject = target ?: throw IllegalArgumentException("没有指定推送对象")
        val task = PixivTimerTask.User(cron = cron.asData(), user = user?.id, subject = subject.id)
        TODO("save $task")
    }

    @SubCommand
    @Description("推送排行榜新作品")
    public suspend fun CommandSender.rank(mode: RankMode, cron: Cron, target: Contact? = subject) {
        val subject = target ?: throw IllegalArgumentException("没有指定推送对象")
        val task = PixivTimerTask.Rank(mode = mode, cron = cron.asData(), user = user?.id, subject = subject.id)
        TODO("save $task")
    }

    @SubCommand
    @Description("推送关注用户作品")
    public suspend fun CommandSender.follow(cron: Cron, target: Contact? = subject) {
        val subject = target ?: throw IllegalArgumentException("没有指定推送对象")
        val task = PixivTimerTask.Follow(cron = cron.asData(), user = user?.id, subject = subject.id)
        TODO("save $task")
    }

    @SubCommand
    @Description("推送推荐作品")
    public suspend fun CommandSender.recommended(cron: Cron, target: Contact? = subject) {
        val subject = target ?: throw IllegalArgumentException("没有指定推送对象")
        val task = PixivTimerTask.Recommended(cron = cron.asData(), user = user?.id, subject = subject.id)
        TODO("save $task")
    }

    @SubCommand
    @Description("推送热门标签")
    public suspend fun CommandSender.trending(cron: Cron, target: Contact? = subject) {
        val subject = target ?: throw IllegalArgumentException("没有指定推送对象")
        val task = PixivTimerTask.Trending(cron = cron.asData(), user = user?.id, subject = subject.id)
        TODO("save $task")
    }

    @SubCommand
    @Description("定时缓存任务")
    public suspend fun CommandSender.cache(uid: Long, cron: Cron, vararg args: String) {
        // TODO: check client
        val task = PixivTimerTask.Cache(
            uid = uid,
            cron = cron.asData(),
            arguments = args.joinToString(separator = " "),
            user = user?.id,
            subject = subject?.id
        )
        TODO("save $task")
    }

    @SubCommand
    @Description("定时任务，删除")
    public suspend fun CommandSender.delete(name: String) {
        val message = try {
            val task = PixivScheduler.remove(name) ?: TODO()
            "定时任务${task}已删除"
        } catch (cause: Throwable) {
            "定时任务${name}删除失败，${cause.message}"
        }

        sendMessage(message)
    }

    @SubCommand
    @Description("查看任务详情")
    public suspend fun CommandSender.detail() {
        val message = buildMessageChain {
//            for ((name, task) in PixivTaskData.tasks) {
//                appendLine("> ---------")
//                appendLine("名称: $name , 间隔: ${task.interval / MINUTE}min")
//                try {
//                    with(StatisticTaskInfo.last(name) ?: continue) {
//                        val time =
//                            OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
//                        appendLine("最后播放作品ID $pid 时间 $time")
//                    }
//                } catch (cause: Throwable) {
//                    appendLine("最后播放作品ID 查询错误 ${cause.findSQLException() ?: cause}")
//                }
//            }

        }
        sendMessage(message = message.ifEmpty { "任务为空".toPlainText() })
    }
}