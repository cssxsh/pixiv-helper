package xyz.cssxsh.mirai.pixiv.command

import com.cronutils.model.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.task.*
import xyz.cssxsh.pixiv.*

public object PixivTaskCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "task",
    description = "PIXIV定时器",
    overrideContext = PixivCommandArgumentContext
), PixivHelperCommand {

    public suspend fun CommandSender.task(block: () -> PixivTimerTask) {
        val message = try {
            val task = block()
            PixivScheduler += task
            "任务 ${task.id} 已设置"
        } catch (casue: Throwable) {
            "任务设置出错"
        }
        sendMessage(message = message)
    }

    @SubCommand
    @Description("推送用户新作品")
    public suspend fun CommandSender.user(uid: Long, cron: Cron, target: Contact? = subject): Unit = task {
        val subject = target ?: throw IllegalArgumentException("没有指定推送对象")

        PixivTimerTask.User(
            uid = uid,
            cron = cron.asData(),
            user = user?.id,
            subject = subject.id
        )
    }

    @SubCommand
    @Description("推送排行榜新作品")
    public suspend fun CommandSender.rank(mode: RankMode, cron: Cron, target: Contact? = subject): Unit = task {
        val subject = target ?: throw IllegalArgumentException("没有指定推送对象")

        PixivTimerTask.Rank(
            mode = mode,
            cron = cron.asData(),
            user = user?.id,
            subject = subject.id
        )
    }

    @SubCommand
    @Description("推送关注用户作品")
    public suspend fun CommandSender.follow(cron: Cron, target: Contact? = subject): Unit = task {
        val subject = target ?: throw IllegalArgumentException("没有指定推送对象")

        PixivTimerTask.Follow(
            cron = cron.asData(),
            user = user?.id,
            subject = subject.id
        )
    }

    @SubCommand
    @Description("推送推荐作品")
    public suspend fun CommandSender.recommended(cron: Cron, target: Contact? = subject): Unit = task {
        val subject = target ?: throw IllegalArgumentException("没有指定推送对象")

        PixivTimerTask.Recommended(
            cron = cron.asData(),
            user = user?.id,
            subject = subject.id
        )
    }

    @SubCommand
    @Description("推送热门标签")
    public suspend fun CommandSender.trending(cron: Cron, target: Contact? = subject): Unit = task {
        val subject = target ?: throw IllegalArgumentException("没有指定推送对象")

        PixivTimerTask.Trending(
            cron = cron.asData(),
            user = user?.id,
            subject = subject.id
        )
    }

    @SubCommand
    @Description("定时缓存任务")
    public suspend fun CommandSender.cache(uid: Long, cron: Cron, vararg args: String): Unit = task {
        val target = subject?.id ?: throw IllegalArgumentException("请在聊天环境运行")

        PixivTimerTask.Cache(
            uid = uid,
            cron = cron.asData(),
            arguments = args.joinToString(separator = " "),
            user = user?.id,
            subject = target
        )
    }

    @SubCommand
    @Description("定时任务，删除")
    public suspend fun CommandSender.cron(id: String, cron: Cron) {
        val message = try {
            when (val task = PixivScheduler[id]) {
                null -> "任务不存在"
                else -> {
                    task.cron = cron.asData()
                    "定时任务${task.id}已设置 corn 为 ${task.cron}"
                }
            }
        } catch (cause: Throwable) {
            "定时任务${id}删除失败，${cause.message}"
        }

        sendMessage(message)
    }

    @SubCommand
    @Description("定时任务，删除")
    public suspend fun CommandSender.delete(id: String) {
        val message = try {
            when (val task = PixivScheduler.remove(id)) {
                null -> "任务不存在"
                else -> "定时任务${task.id}已删除"
            }
        } catch (cause: Throwable) {
            "定时任务${id}删除失败，${cause.message}"
        }

        sendMessage(message)
    }

    @SubCommand
    @Description("查看任务详情")
    public suspend fun CommandSender.detail() {
        sendMessage(message = PixivScheduler.detail().ifEmpty { "任务列表为空" })
    }
}