package xyz.cssxsh.mirai.pixiv.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.model.*

public object PixivTagCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "tag", "标签", "[饥饿]",
    description = "PIXIV标签"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    private fun record(tag: String, pid: Long?, event: MessageEvent) {
        StatisticTagInfo(
            sender = event.sender.id,
            group = (event.subject as? Group)?.id,
            pid = pid,
            tag = tag,
            timestamp = event.time.toLong()
        ).persist()
    }

    @Handler
    public suspend fun CommandSenderOnMessage<*>.handle(word: String, bookmarks: Long = 0): Unit = withHelper {
        val artwork = tag(word = word, bookmarks = bookmarks, fuzzy = false)
            ?: tag(word = word, bookmarks = bookmarks, fuzzy = true)

        launch(SupervisorJob()) {
            record(tag = word, pid = artwork?.pid, event = fromEvent)
        }

        artwork ?: "$subject 读取Tag[${word}]色图失败, 请尝试日文和英文"
    }
}