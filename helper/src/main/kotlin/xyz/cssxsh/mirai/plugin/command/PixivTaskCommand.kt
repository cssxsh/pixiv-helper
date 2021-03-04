package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.event.events.MessageEvent
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
    private const val TASK_DURATION = 60

    @SubCommand
    fun CommandSenderOnMessage<MessageEvent>.user(uid: Long, duration: Int = TASK_DURATION) = getHelper().run {
        PixivHelperScheduler.setTimerTask(
            name = "User($uid)[${contact}]",
            info = TimerTask.User(
                uid = uid,
                interval = duration.minutes.toLongMilliseconds(),
                contact = getContactInfo()
            )
        )
    }

    @SubCommand
    fun CommandSenderOnMessage<MessageEvent>.rank(mode: RankMode) = getHelper().run {
        PixivHelperScheduler.setTimerTask(
            name = "Rank($mode)[${contact}]",
            info = TimerTask.Rank(
                mode = mode,
                contact = getContactInfo()
            )
        )
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.follow(duration: Int = TASK_DURATION) = getHelper().run {
        PixivHelperScheduler.setTimerTask(
            name = "Follow(${getAuthInfo().user.uid})[${contact}]",
            info = TimerTask.Follow(
                interval = duration.minutes.toLongMilliseconds(),
                contact = getContactInfo()
            )
        )
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.recommended(duration: Int = TASK_DURATION) = getHelper().run {
        PixivHelperScheduler.setTimerTask(
            name = "Recommended(${getAuthInfo().user.uid})[${contact}]",
            info = TimerTask.Recommended(
                interval = duration.minutes.toLongMilliseconds(),
                contact = getContactInfo()
            )
        )
    }

    @SubCommand
    fun CommandSender.backup(duration: Int = TASK_DURATION) = PixivHelperScheduler
        .setTimerTask(name = "Backup", info = TimerTask.Backup(interval = duration.minutes.toLongMilliseconds()))
}