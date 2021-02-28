package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger

@Suppress("unused")
object PixivGetCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "get", "搞快点",
    description = "PIXIV获取指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.get(pid: Long, flush: Boolean = false) = getHelper().runCatching {
        buildMessageByIllust(getIllustInfo(pid = pid, flush = flush))
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        logger.warning({ "读取色图失败" }, it)
        quoteReply("读取色图失败， ${it.message}")
    }.isSuccess
}