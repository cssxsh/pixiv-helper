package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.contact.recall
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.QuoteReply
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin

object PixivRecallCommand : SimpleCommand(
    PixivHelperPlugin,
    "recall", "撤回",
    description = "色图指令",
    prefixOptional = true
), PixivHelperLogger {

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle()  {
        message[QuoteReply]?.runCatching {
            fromEvent.subject.recall(source)
        }
    }
}