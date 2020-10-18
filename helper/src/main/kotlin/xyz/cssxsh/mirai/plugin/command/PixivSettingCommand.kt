package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.PixivHelper.Companion.DATE_FORMAT_CHINESE
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivConfigData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.mirai.plugin.data.PixivStatisticalData
import xyz.cssxsh.mirai.plugin.getHelper

@Suppress("unused")
object PixivSettingCommand: CompositeCommand(
    PixivHelperPlugin,
    "set",
    description = "pixiv 设置",
    prefixOptional = true
), PixivHelperLogger {
    /**
     * 设置代理 pixiv proxy http://10.21.159.95:7890
     * @param proxy 代理URL
     */
    @SubCommand
    fun ConsoleCommandSender.proxy(proxy: String) {
        logger.info("proxy: ${PixivConfigData.config.proxy} -> $proxy")
        PixivConfigData.config.proxy = proxy
    }

    /**
     * 设置色图更新间隔
     */
    @SubCommand
    fun ConsoleCommandSender.interval(interval: Int) {
        logger.info("interval: ${PixivHelperSettings.minInterval} -> $interval")
        PixivHelperSettings.minInterval = interval
    }

    /**
     * 获取助手信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.info() = getHelper().runCatching {
        buildString {
            appendLine("账户: ${getAuthInfo().user.account}")
            appendLine("Token: ${getAuthInfo().accessToken}")
            appendLine("ExpiresTime: ${expiresTime.format(DATE_FORMAT_CHINESE)}")
            appendLine("简略信息: $simpleInfo")
            appendLine("缓存数: ${PixivCacheData.caches().size}")
            appendLine("全年龄色图数: ${PixivCacheData.eros().size}")
            appendLine("R18色图数: ${PixivCacheData.r18s().size}")
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 获取用户信息
     */
    suspend fun CommandSenderOnMessage<MessageEvent>.user(target: User) = runCatching {
        PixivStatisticalData.getCount(target).let { (ero, tags) ->
            buildString {
                appendLine("账户: ${target}")
                appendLine("使用色图指令次数: $ero")
                appendLine("使用tag指令次数: $tags")
            }
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

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