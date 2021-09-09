package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*

object PixivEroCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "ero", "色图", "涩图", "瑟图",
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

    private fun good(sanity: Int, marks: Long) = caches.values.filter { info ->
        info.sanity >= sanity && info.bookmarks > marks
    }

    private fun randomEroArtWorkInfos(sanity: Int, marks: Long): List<ArtWorkInfo> {
        if (good(sanity, marks).isEmpty()) {
            ArtWorkInfo.random(sanity, marks, AgeLimit.ALL, EroChunk).forEach { info ->
                synchronized(caches) {
                    caches[info.pid] = info
                }
            }
        }
        return good(sanity, marks)
    }

    private fun CommandSenderOnMessage<*>.record(pid: Long) = launch(SupervisorJob()) {
        StatisticEroInfo(
            sender = fromEvent.sender.id,
            group = (fromEvent.subject as? Group)?.id,
            pid = pid,
            timestamp = fromEvent.time.toLong()
        ).replicate()
    }

    private val History.expire get() = (System.currentTimeMillis() - last) > EroUpExpire

    @Handler
    suspend fun CommandSenderOnMessage<*>.ero() = withHelper {
        histories.getOrPut(fromEvent.subject) { History() }.let { history ->
            if ("更色" in fromEvent.message.content) {
                history.minSanityLevel++
            } else {
                history.minSanityLevel = 0
            }
            if ("更好" !in fromEvent.message.content && history.expire) {
                history.minBookmarks = 0
            }
            randomEroArtWorkInfos(history.minSanityLevel, history.minBookmarks).randomOrNull().also { info ->
                requireNotNull(info) { "sanity >= ${history.minSanityLevel}, bookmarks >= ${history.minBookmarks}, 随机失败，请刷慢一点哦" }
                synchronized(caches) {
                    caches.remove(info.pid)
                    history.minSanityLevel = info.sanity
                    history.minBookmarks = info.bookmarks
                    history.last = System.currentTimeMillis()
                    record(pid = info.pid)
                }
            }
        }
    }
}