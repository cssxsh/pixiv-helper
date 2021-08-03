package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*

object PixivEroCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "ero", "色图", "涩图",
    description = "PIXIV色图指令"
) {

    override val prefixOptional: Boolean = true

    private val caches: MutableMap<Long, ArtWorkInfo> = mutableMapOf()

    private data class History(
        var last: Long = System.currentTimeMillis(),
        var minSanityLevel: Int = 0,
        var minBookmarks: Long = 0
    )

    private val histories: MutableMap<Contact, History> = mutableMapOf()

    private fun History.good() = caches.values.filter { info ->
        info.sanityLevel >= minSanityLevel && info.totalBookmarks > minBookmarks
    }

    private fun History.getEroArtWorkInfos(): List<ArtWorkInfo> {
        val result = good()
        if (result.isEmpty()) {
            ArtWorkInfo.random(minSanityLevel, minBookmarks, EroInterval).forEach { info -> caches[info.pid] = info }
        }
        return good()
    }

    private fun eroStatisticAdd(event: MessageEvent, pid: Long) {
        StatisticEroInfo(
            sender = event.sender.id,
            group = event.subject.takeIf { it is Group }?.id,
            pid = pid,
            timestamp = event.time.toLong()
        ).saveOrUpdate()
    }

    private val expire get() = System.currentTimeMillis() - EroUpExpire

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