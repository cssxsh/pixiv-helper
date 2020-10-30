package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.contact.recall
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.QuoteReply
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin

object PixivRecallCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "recall", "撤回",
    description = "撤回指令"
), PixivHelperLogger {

    override val prefixOptional: Boolean = true

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle()  {
        message[QuoteReply]?.runCatching {
            logger.verbose("尝试对${source}进行撤回")
            fromEvent.subject.recall(source)
        }
    }
}