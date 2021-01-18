package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.minInterval
import xyz.cssxsh.pixiv.model.ArtWorkInfo

@Suppress("unused")
object PixivEroCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "ero", "色图", "涩图",
    description = "PIXIV色图指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private val caches: MutableMap<Long, ArtWorkInfo> = mutableMapOf()

    private fun PixivHelper.addEroArtWorkInfos() = useArtWorkInfoMapper { it.eroRandom(minInterval) }.forEach { info ->
        caches[info.pid] = info
    }

    private fun PixivHelper.getEroArtWorkInfos(): List<ArtWorkInfo> = caches.values.filter { info ->
        info.pid !in historyQueue && info.sanityLevel >= minSanityLevel && info.totalBookmarks > minBookmarks
    }.let { infos ->
        if (infos.isEmpty()) {
            addEroArtWorkInfos()
            getEroArtWorkInfos()
        } else {
            infos
        }
    }

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle() = getHelper().runCatching {
        if ("更色" in message.contentToString()) {
            minSanityLevel++
        } else {
            minSanityLevel = 0
        }
        if ("更好" !in message.contentToString()) {
            minBookmarks = 0
        }
        // TODO 使用缓存变量
        getEroArtWorkInfos().apply {
            PixivStatisticalData.eroAdd(user = fromEvent.sender).let {
                logger.verbose { "${fromEvent.sender}第${it}次使用色图, 最小健全等级${minSanityLevel}, 最小收藏数${minBookmarks} 共找到${size} 张色图" }
            }
        }.random().also { info ->
            minSanityLevel = info.sanityLevel
            minBookmarks = info.totalBookmarks
            historyQueue.apply {
                if (remainingCapacity() == 0) {
                    take()
                }
                put(info.pid)
            }
        }.let { info ->
            buildMessageByIllust(info.pid)
        }
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        logger.warning({ "读取色图失败" }, it)
        quoteReply("读取色图失败， ${it.message}")
    }.isSuccess
}

















