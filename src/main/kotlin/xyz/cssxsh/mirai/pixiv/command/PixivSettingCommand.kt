package xyz.cssxsh.mirai.pixiv.command

import net.mamoe.mirai.console.command.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.data.*

public object PixivSettingCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "setting",
    description = "PIXIV设置"
), PixivHelperCommand {

    @SubCommand
    @Description("设置Task连续发送间隔时间, 单位秒")
    public suspend fun CommandSender.interval(sec: Int) {
        val old = PixivConfigData.interval
        PixivConfigData.interval = sec
        sendMessage(message = "$old -> ${sec}s")
    }

    @SubCommand
    @Description("设置Task通过转发发送")
    public suspend fun CommandSender.forward() {
        PixivConfigData.forward = !TaskForward
        sendMessage(message = "$TaskForward")
    }

    @SubCommand
    @Description("设置是否显示Pixiv Cat 原图链接")
    public suspend fun UserCommandSender.link(): Unit = withHelper {
        link = !link
        link
    }

    @SubCommand
    @Description("设置是否显示TAG INFO")
    public suspend fun UserCommandSender.tag(): Unit = withHelper {
        tag = !tag
        tag
    }

    @SubCommand
    @Description("设置是否显示作品属性")
    public suspend fun UserCommandSender.attr(): Unit = withHelper {
        attr = !attr
        attr
    }

    @SubCommand
    @Description("设置是否显示最大图片数")
    public suspend fun UserCommandSender.max(num: Int): Unit = withHelper {
        val old = max
        max = num
        "$old -> $num"
    }

    @SubCommand
    @Description("设置发送模式, type: NORMAL, FLASH, RECALL, FORWARD")
    public suspend fun UserCommandSender.model(type: String, ms: Long = 60_000L): Unit = withHelper {
        val new = SendModel(type, ms)
        val old = model
        model = new
        "$old -> $new"
    }
}