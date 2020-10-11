package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.pixiv.api.app.illustRelated
import xyz.cssxsh.pixiv.data.app.IllustInfo

object PixivTagCommand: SimpleCommand(
    PixivHelperPlugin,
    "tag", "标签",
    description = "pixiv 标签",
    prefixOptional = true
), PixivHelperLogger {

    private val jobs : MutableList<Job> = mutableListOf()

    private fun PixivHelper.searchTag(illust: IllustInfo, seedIllusts: List<IllustInfo>) = launch {
        illustRelated(illust.pid, seedIllusts.map { it.pid }).illusts.forEach {
            PixivCacheData.add(it)
        }
    }.also {
        jobs.add(it)
    }

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(tag: String) = getHelper().runCatching {
        PixivCacheData.eros.values.filter { illust ->
            tag in illust.title || illust.tags.any { tag in it.name || tag in it.translatedName ?: "" }
        }.let { list ->
            buildMessage(list.random().also { searchTag(it, list) })
        }
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("读取色图失败， ${it.message}")
    }.isSuccess
}