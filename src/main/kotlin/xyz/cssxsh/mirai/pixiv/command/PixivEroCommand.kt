package xyz.cssxsh.mirai.pixiv.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.model.*

public object PixivEroCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "ero", "色图", "涩图", "瑟图", "[色]",
    description = "PIXIV色图指令"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    private data class History(
        var last: Long = System.currentTimeMillis(),
        var sanity: Int = 0,
        var bookmarks: Long = 0
    )

    private val History.expire get() = (System.currentTimeMillis() - last) > EroUpExpire

    private val histories: MutableMap<Long, History> = java.util.concurrent.ConcurrentHashMap()

    private fun record(pid: Long, event: MessageEvent) {
        StatisticEroInfo(
            sender = event.sender.id,
            group = (event.subject as? Group)?.id,
            pid = pid,
            timestamp = event.time.toLong()
        ).persist()
    }

    @Handler
    public suspend fun CommandSenderOnMessage<*>.handle(): Unit = withHelper {
        if (shake()) return@withHelper null
        val history = histories.getOrPut(fromEvent.subject.id) { History() }
        if ("更色" in fromEvent.message.content) {
            history.sanity++
        } else {
            history.sanity = 0
        }
        if ("更好" !in fromEvent.message.content && history.expire) {
            history.bookmarks = 0
        }

        val artwork = ero(sanity = history.sanity, bookmarks = history.bookmarks)
            ?: return@withHelper "sanity >= ${history.sanity}, bookmarks >= ${history.bookmarks}, 随机失败，请刷慢一点哦"

        launch(SupervisorJob()) {
            history.last = System.currentTimeMillis()
            record(pid = artwork.pid, event = fromEvent)
        }

        artwork
    }
}