package xyz.cssxsh.mirai.pixiv.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import xyz.cssxsh.mirai.pixiv.*

public object PixivGetCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "get", "搞快点", "GKD", "[勾引]", "pid",
    description = "PIXIV获取指令"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    @Handler
    public suspend fun UserCommandSender.get(pid: Long, flush: Boolean = false): Unit = withHelper {
        client.getIllustInfo(pid = pid, flush = flush)
    }
}