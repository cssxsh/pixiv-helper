package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.minInterval
import xyz.cssxsh.pixiv.model.*

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

    private data class History(
        val list: MutableList<Long> = mutableListOf(),
        var minSanityLevel: Int = 0,
        var minBookmarks: Long = 0
    )

    private val histories: MutableMap<Contact, History> = mutableMapOf()

    private fun History.addEroArtWorkInfos() = useArtWorkInfoMapper { it.eroRandom(minInterval) }.forEach { info ->
        caches[info.pid] = info
    }

    private fun History.getEroArtWorkInfos(): List<ArtWorkInfo> = caches.values.filter { info ->
        info.pid !in list && info.sanityLevel >= minSanityLevel && info.totalBookmarks > minBookmarks
    }.ifEmpty {
        addEroArtWorkInfos()
        getEroArtWorkInfos()
    }

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle() = histories.getOrPut(fromEvent.subject) { History() }.runCatching {
        if ("更色" in fromEvent.message.contentToString()) {
            minSanityLevel++
        } else {
            minSanityLevel = 0
        }
        if ("更好" !in fromEvent.message.contentToString()) {
            minBookmarks = 0
        }
        getEroArtWorkInfos().apply {
            PixivStatisticalData.eroAdd(user = fromEvent.sender).let {
                logger.verbose { "${fromEvent.sender}第${it}次使用色图, 最小健全等级${minSanityLevel}, 最小收藏数${minBookmarks} 共找到${size} 张色图" }
            }
        }.random().also { info ->
            minSanityLevel = info.sanityLevel
            minBookmarks = info.totalBookmarks
            list.apply {
                if (size >= minInterval) {
                    clear()
                }
                add(info.pid)
            }
        }.let { info ->
            getHelper().buildMessageByIllust(info.pid)
        }
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        logger.warning({ "读取色图失败" }, it)
        quoteReply("读取色图失败， ${it.message}")
    }.isSuccess
}

















