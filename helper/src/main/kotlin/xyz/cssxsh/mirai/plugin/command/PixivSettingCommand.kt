package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*

@Suppress("unused")
object PixivSettingCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "setting",
    description = "PIXIV设置"
) {

    @SubCommand
    @Description("设置代理, 例如 http://127.0.0.1:1080")
    fun ConsoleCommandSender.proxy(proxy: String) {
        logger.info { "proxy: ${PixivConfigData.default.proxy} -> $proxy" }
        PixivConfigData.default.proxy = proxy
    }

    @SubCommand
    @Description("设置是否显示Pixiv Cat 原图链接")
    suspend fun CommandSenderOnMessage<*>.link(link: Boolean) = withHelper {
        "$link -> $link".also { this.link = link }
    }
}