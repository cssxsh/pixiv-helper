package xyz.cssxsh.mirai.pixiv.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.model.*

object PixivEroCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "ero", "色图", "涩图", "瑟图", "[色]",
    description = "PIXIV色图指令"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    private val caches: MutableMap<Long, ArtWorkInfo> = HashMap()

    private data class History(
        var last: Long = System.currentTimeMillis(),
        var minSanityLevel: Int = 0,
        var minBookmarks: Long = 0
    )

    private val histories: MutableMap<Contact, History> = HashMap()

    private fun good(sanity: Int, marks: Long) = caches.values.filter { it.sanity >= sanity && it.bookmarks > marks }

    private fun randomEroArtWorkInfos(sanity: Int, marks: Long): List<ArtWorkInfo> {
        if (good(sanity, marks).isEmpty()) {
            for (info in ArtWorkInfo.random(sanity, marks, EroAgeLimit, EroChunk)) {
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

    internal operator fun plusAssign(list: List<ArtWorkInfo>) = synchronized(caches) {
        caches += list.filter { it.ero }.associateBy { it.pid }
    }

    @Handler
    suspend fun CommandSenderOnMessage<*>.ero() = withHelper {
        with(histories.getOrPut(fromEvent.subject) { History() }) {
            if ("更色" in fromEvent.message.content) {
                minSanityLevel++
            } else {
                minSanityLevel = 0
            }
            if ("更好" !in fromEvent.message.content && expire) {
                minBookmarks = 0
            }

            val info = randomEroArtWorkInfos(minSanityLevel, minBookmarks).randomOrNull()
                ?: return@with "sanity >= ${minSanityLevel}, bookmarks >= ${minBookmarks}, 随机失败，请刷慢一点哦"

            synchronized(caches) {
                caches.remove(info.pid)
                minSanityLevel = info.sanity
                minBookmarks = info.bookmarks
                last = System.currentTimeMillis()
                record(pid = info.pid)
            }

            info
        }
    }
}