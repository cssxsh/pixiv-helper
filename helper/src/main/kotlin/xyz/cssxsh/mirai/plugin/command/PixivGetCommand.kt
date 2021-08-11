package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import xyz.cssxsh.mirai.plugin.*

object PixivGetCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "get", "搞快点", "GKD",
    description = "PIXIV获取指令"
) {

    override val prefixOptional: Boolean = true

    @Handler
    suspend fun CommandSenderOnMessage<*>.get(pid: Long, flush: Boolean = false) = withHelper {
        getIllustInfo(pid = pid, flush = flush)
    }
}