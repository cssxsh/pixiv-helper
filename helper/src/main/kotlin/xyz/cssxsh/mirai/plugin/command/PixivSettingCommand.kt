package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*

object PixivSettingCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "setting",
    description = "PIXIV设置"
) {

    @SubCommand
    @Description("设置连续发送间隔时间, 单位秒")
    suspend fun CommandSenderOnMessage<*>.interval(sec: Int) = withHelper {
        "$SendInterval -> ${sec}s".also { PixivConfigData.interval = sec }
    }

    @SubCommand
    @Description("设置是否显示Pixiv Cat 原图链接")
    suspend fun CommandSenderOnMessage<*>.link(open: Boolean) = withHelper {
        "$link -> $open".also { link = open }
    }

    @SubCommand
    @Description("设置是否显示TAG INFO")
    suspend fun CommandSenderOnMessage<*>.tag(open: Boolean) = withHelper {
        "$tag -> $open".also { tag = open }
    }

    @SubCommand
    @Description("设置是否显示作品属性")
    suspend fun CommandSenderOnMessage<*>.attr(open: Boolean) = withHelper {
        "$attr -> $open".also { attr = open }
    }

    @SubCommand
    @Description("设置是否显示最大图片数")
    suspend fun CommandSenderOnMessage<*>.max(num: Int) = withHelper {
        "$max -> $num".also { max = num }
    }
}