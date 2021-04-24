package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*

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

    private val expire get() = System.currentTimeMillis() - ERO_UP_EXPIRE.toLongMilliseconds()

    @Handler
    suspend fun CommandSenderOnMessage<*>.ero() = sendIllust {
        histories.getOrPut(fromEvent.subject) { History() }.let { history ->
            if ("更色" in fromEvent.message.contentToString()) {
                history.minSanityLevel++
            } else {
                history.minSanityLevel = 0
            }
            if ("更好" !in fromEvent.message.contentToString() && history.last < expire) {
                history.minBookmarks = 0
            }
            history.getEroArtWorkInfos().random().also { info ->
                synchronized(history) {
                    history.minSanityLevel = info.sanityLevel
                    history.minBookmarks = info.totalBookmarks
                    caches.remove(info.pid)
                    history.last = System.currentTimeMillis()
                    eroStatisticAdd(fromEvent, info.pid)
                }
            }
        }
    }
}

















