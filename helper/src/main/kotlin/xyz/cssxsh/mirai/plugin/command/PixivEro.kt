@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.buildMessage
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.minInterval
import xyz.cssxsh.mirai.plugin.getHelper
import xyz.cssxsh.pixiv.data.app.IllustInfo
import java.util.concurrent.ArrayBlockingQueue

object PixivEro : SimpleCommand(
    PixivHelperPlugin,
    "ero", "色图",
    description = "色图指令",
    prefixOptional = true
) {
    private val historyQueue = ArrayBlockingQueue<Long>(minInterval)

    private fun randomIllust(): IllustInfo = PixivCacheData.illusts.values.random().let { illust ->
        if ((illust.totalBookmarks ?: 0) >= 5000 && illust.pid !in historyQueue) {
            illust
        } else {
            PixivHelperPlugin.logger.verbose("色图不够色！${illust.pid}, 再来")
            randomIllust()
        }
    }

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle() = getHelper().runCatching {
        buildMessage(randomIllust().also {
            historyQueue.put(it.pid)
        })
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}

















