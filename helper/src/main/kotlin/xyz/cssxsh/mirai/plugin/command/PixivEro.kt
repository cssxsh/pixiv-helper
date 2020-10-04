@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.buildMessage
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.getHelper
import xyz.cssxsh.mirai.plugin.isR18

object PixivEro : SimpleCommand(
    PixivHelperPlugin,
    "ero", "色图",
    description = "色图指令",
    prefixOptional = true
) {
    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle() = getHelper().runCatching {
        PixivCacheData.illust.filterValues {
            (it.totalBookmarks ?: 0) >= 5000
        }.values.random().let { buildMessage(it) }
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}