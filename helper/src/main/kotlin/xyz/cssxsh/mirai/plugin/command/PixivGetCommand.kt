package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger

object PixivGetCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "get", "搞快点",
    description = "PIXIV获取指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(pid: Long) = getHelper().runCatching {
        buildMessageByIllust(getIllustInfo(pid = pid, flush = true))
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        logger.warning({ "读取色图失败" }, it)
        quoteReply("读取色图失败， ${it.message}")
    }.isSuccess
}