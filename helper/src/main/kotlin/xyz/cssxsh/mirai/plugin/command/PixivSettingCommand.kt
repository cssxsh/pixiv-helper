package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.utils.info
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.PixivConfigData
import xyz.cssxsh.mirai.plugin.getHelper

@Suppress("unused")
object PixivSettingCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "set",
    description = "PIXIV设置"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

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