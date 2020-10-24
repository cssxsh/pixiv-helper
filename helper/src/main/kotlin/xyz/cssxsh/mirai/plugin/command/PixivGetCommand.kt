package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*

object PixivGetCommand: SimpleCommand(
    owner = PixivHelperPlugin,
    "get", "搞快点",
    description = "获取指令",
    prefixOptional = true
), PixivHelperLogger {

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(pid: Long) = getHelper().runCatching {
        buildMessage(getIllustInfo(pid))
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("读取色图失败， ${it.message}")
    }.isSuccess
}