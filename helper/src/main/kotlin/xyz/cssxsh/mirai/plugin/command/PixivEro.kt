@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.pixiv.data.app.IllustInfo
import java.util.concurrent.ArrayBlockingQueue

object PixivEro : SimpleCommand(
    PixivHelperPlugin,
    "ero", "色图",
    description = "色图指令",
    prefixOptional = true
), PixivHelperLogger {
    private val historyQueue = ArrayBlockingQueue<Long>(PixivHelperSettings.minInterval)

    private fun randomIllust(): IllustInfo = PixivCacheData.values.random().let { illust ->
        if (illust.totalBookmarks ?: 0 >= 10000 &&
            illust.pid !in historyQueue &&
            illust.sanityLevel > 4 &&
            illust.isR18().not() &&
            illust.pageCount == 1) {
            illust
        } else {
            logger.verbose("${illust.pid} 不够色, 再来")
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

















