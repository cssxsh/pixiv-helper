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
    suspend fun CommandSenderOnMessage<*>.link(link: Boolean) = withHelper {
        "$link -> $link".also { this.link = link }
    }
}