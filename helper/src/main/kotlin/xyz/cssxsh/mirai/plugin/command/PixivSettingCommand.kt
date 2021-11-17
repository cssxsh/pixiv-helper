package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*

object PixivSettingCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "setting",
    description = "PIXIV设置"
), PixivHelperCommand {

    @SubCommand
    @Description("设置Task连续发送间隔时间, 单位秒")
    suspend fun UserCommandSender.interval(sec: Int) = withHelper {
        val old = PixivConfigData.interval
        PixivConfigData.interval = sec
        "$old -> ${sec}s"
    }

    @SubCommand
    @Description("设置Task通过转发发送")
    suspend fun UserCommandSender.forward() = withHelper {
        PixivConfigData.forward = !TaskForward
        TaskForward
    }

    @SubCommand
    @Description("设置是否显示Pixiv Cat 原图链接")
    suspend fun UserCommandSender.link() = withHelper {
        link = !link
        link
    }

    @SubCommand
    @Description("设置是否显示TAG INFO")
    suspend fun UserCommandSender.tag() = withHelper {
        tag = !tag
        tag
    }

    @SubCommand
    @Description("设置是否显示作品属性")
    suspend fun UserCommandSender.attr() = withHelper {
        attr = !attr
        attr
    }

    @SubCommand
    @Description("设置cooling置零")
    suspend fun UserCommandSender.cooling() = withHelper {
        PixivTagCommand.cooling[contact.id] = 0
        "当前用户已置零"
    }

    @SubCommand
    @Description("设置是否显示最大图片数")
    suspend fun UserCommandSender.max(num: Int) = withHelper {
        val old = max
        max = num
        "$old -> $num"
    }

    @SubCommand
    @Description("设置发送模式, type: NORMAL, FLASH, RECALL, FORWARD")
    suspend fun UserCommandSender.model(type: String, ms: Long = 60_000L) = withHelper {
        val new = SendModel(type, ms)
        val old = model
        model = new
        "$old -> $new"
    }
}