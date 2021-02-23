package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.utils.info
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.*

@Suppress("unused")
object PixivSettingCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "setting",
    description = "PIXIV设置"
) {

    /**
     * 设置代理 pixiv proxy http://10.21.159.95:7890
     * @param proxy 代理URL
     */
    @SubCommand
    fun ConsoleCommandSender.proxy(proxy: String) {
        logger.info { "proxy: ${PixivConfigData.default.proxy} -> $proxy" }
        PixivConfigData.default.proxy = proxy
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.simple(
        isSimple: Boolean
    ) = getHelper().runCatching {
        "$simpleInfo -> $isSimple".also { simpleInfo = isSimple }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}