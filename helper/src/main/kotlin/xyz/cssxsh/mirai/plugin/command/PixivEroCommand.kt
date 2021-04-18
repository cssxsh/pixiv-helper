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
        var last: Long = System.currentTimeMillis(),
        var minSanityLevel: Int = 0,
        var minBookmarks: Long = 0
    )

    private val histories: MutableMap<Contact, History> = mutableMapOf()

    private fun History.addEroArtWorkInfos() = useMappers { it.artwork.eroRandom(PixivHelperSettings.eroInterval) }.forEach { info ->
        caches[info.pid] = info
    }

    private fun History.getEroArtWorkInfos(): List<ArtWorkInfo> = caches.values.filter { info ->
        info.sanityLevel >= minSanityLevel && info.totalBookmarks > minBookmarks
    }.ifEmpty { addEroArtWorkInfos(); getEroArtWorkInfos() }

    private fun eroStatisticAdd(event: MessageEvent, pid: Long): Boolean = useMappers { mappers ->
        mappers.statistic.replaceEroInfo(StatisticEroInfo(
            sender = event.sender.id,
            group = event.subject.takeIf { it is Group }?.id,
            pid = pid,
            timestamp = event.time.toLong()
        ))
    }

    @Handler
    suspend fun CommandSenderOnMessage<*>.ero() = histories.getOrPut(fromEvent.subject) { History() }.runCatching {
        if ("更色" in fromEvent.message.contentToString()) {
            minSanityLevel++
        } else {
            minSanityLevel = 0
        }
        if ("更好" !in fromEvent.message.contentToString() && System.currentTimeMillis() - last > ERO_UP_DURATION.toLongMilliseconds()) {
            minBookmarks = 0
        }
        getEroArtWorkInfos().random().also { info ->
            synchronized(this) {
                minSanityLevel = info.sanityLevel
                minBookmarks = info.totalBookmarks
                caches.remove(info.pid)
                last = System.currentTimeMillis()
                eroStatisticAdd(fromEvent, info.pid)
            }
        }.let { info ->
            getHelper().buildMessageByIllust(pid = info.pid, flush = false)
        }
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        logger.warning({ "读取色图失败" }, it)
        quoteReply("读取色图失败， ${it.message}")
    }.isSuccess
}

















