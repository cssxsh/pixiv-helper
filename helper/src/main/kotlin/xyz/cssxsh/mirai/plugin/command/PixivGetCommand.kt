package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import xyz.cssxsh.mirai.plugin.*

@Suppress("unused")
object PixivGetCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "get", "搞快点", "GKD",
    description = "PIXIV获取指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    @Handler
    suspend fun CommandSenderOnMessage<*>.get(pid: Long, flush: Boolean = false) = sendIllust(flush = flush) {
        getIllustInfo(pid = pid, flush = flush)
    }
}