package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.data.PixivConfigData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.mirai.plugin.getHelper

@Suppress("unused")
object PixivSettingCommand: CompositeCommand(
    owner = PixivHelperPlugin,
    "set",
    description = "pixiv 设置"
), PixivHelperLogger {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    /**
     * 设置代理 pixiv proxy http://10.21.159.95:7890
     * @param proxy 代理URL
     */
    @SubCommand
    fun ConsoleCommandSender.proxy(proxy: String) {
        logger.info("proxy: ${PixivConfigData.default.proxy} -> $proxy")
        PixivConfigData.default.proxy = proxy
    }

    /**
     * 设置色图更新间隔
     */
    @SubCommand
    fun ConsoleCommandSender.interval(interval: Int) {
        logger.info("interval: ${PixivHelperSettings.minInterval} -> $interval")
        PixivHelperSettings.minInterval = interval
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

    @SubCommand
    fun CommandSenderOnMessage<MessageEvent>.bookmark(total: Long) {
        logger.info("totalBookmarks: ${PixivHelperSettings.totalBookmarks} -> $total")
        PixivHelperSettings.totalBookmarks = total
    }
}