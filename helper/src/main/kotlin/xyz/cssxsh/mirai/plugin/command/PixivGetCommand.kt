package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import xyz.cssxsh.mirai.plugin.*

object PixivGetCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "get", "搞快点", "GKD", "[勾引]",
    description = "PIXIV获取指令"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    @Handler
    suspend fun UserCommandSender.get(pid: Long, flush: Boolean = false) = withHelper {
        getIllustInfo(pid = pid, flush = flush)
    }
}