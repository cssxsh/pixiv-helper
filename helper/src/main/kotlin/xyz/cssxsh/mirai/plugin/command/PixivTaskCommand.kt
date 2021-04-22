package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.pixiv.*
import kotlin.time.*

@Suppress("unused")
object PixivTaskCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "task",
    description = "PIXIV定时器",
    overrideContext = PixivCommandArgumentContext
) {
    private const val TASK_DURATION = 3

    @SubCommand
    @Description("设置用户定时订阅任务")
    fun CommandSenderOnMessage<*>.user(uid: Long, duration: Int = TASK_DURATION) = getHelper().run {
        PixivHelperScheduler.setTimerTask(
            name = "User($uid)[${contact}]",
            info = TimerTask.User(uid = uid, interval = duration.hours.toLongMilliseconds(), contact = getContactInfo())
        )
    }

    @SubCommand
    @Description("设置排行榜定时订阅任务")
    fun CommandSenderOnMessage<*>.rank(mode: RankMode) = getHelper().run {
        PixivHelperScheduler.setTimerTask(
            name = "Rank($mode)[${contact}]",
            info = TimerTask.Rank(mode = mode, contact = getContactInfo())
        )
    }

    @SubCommand
    @Description("设置关注推送定时订阅任务")
    suspend fun CommandSenderOnMessage<*>.follow(duration: Int = TASK_DURATION) = getHelper().run {
        PixivHelperScheduler.setTimerTask(
            name = "Follow(${getAuthInfo().user.uid})[${contact}]",
            info = TimerTask.Follow(interval = duration.hours.toLongMilliseconds(), contact = getContactInfo())
        )
    }

    @SubCommand
    @Description("设置推荐画师定时订阅任务")
    suspend fun CommandSenderOnMessage<*>.recommended(duration: Int = TASK_DURATION) = getHelper().run {
        PixivHelperScheduler.setTimerTask(
            name = "Recommended(${getAuthInfo().user.uid})[${contact}]",
            info = TimerTask.Recommended(interval = duration.hours.toLongMilliseconds(), contact = getContactInfo())
        )
    }

    @SubCommand
    @Description("设置定时备份任务")
    fun CommandSenderOnMessage<*>.backup(duration: Int = TASK_DURATION) = getHelper().run {
        PixivHelperScheduler.setTimerTask(
            name = "Backup",
            info = TimerTask.Backup(interval = duration.hours.toLongMilliseconds(), contact = getContactInfo())
        )
    }
}