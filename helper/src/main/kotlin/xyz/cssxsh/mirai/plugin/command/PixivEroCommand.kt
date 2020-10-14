package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.BaseInfo
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivStatisticalData

@Suppress("unused")
object PixivEroCommand : SimpleCommand(
    PixivHelperPlugin,
    "ero", "色图", "涩图", "不够色",
    description = "色图指令",
    prefixOptional = true
), PixivHelperLogger {

    private fun PixivHelper.randomIllust(): BaseInfo = PixivCacheData.eros().random().takeIf { illust ->
        illust.pid !in historyQueue && illust.sanityLevel >= minSanityLevel
    }?.also { info ->
        historyQueue.apply {
            if (remainingCapacity() == 0) take()
            put(info.pid)
            minSanityLevel = info.sanityLevel
        }
    } ?: randomIllust()

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle() = getHelper().runCatching {
        if ("不够色" in message.contentToString()) {
            minSanityLevel++
        } else {
            minSanityLevel = 0
        }
        PixivStatisticalData.eroAdd(id = fromEvent.sender.id).let {
            logger.verbose("${fromEvent.sender}第${it}次使用色图, 搜素等级${minSanityLevel}")
        }
        buildMessage(randomIllust())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("读取色图失败， ${it.message}")
    }.isSuccess
}

















