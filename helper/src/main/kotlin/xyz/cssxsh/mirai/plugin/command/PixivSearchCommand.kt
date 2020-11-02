package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.tools.ImageSearcher

@Suppress("unused")
object PixivSearchCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "search", "搜索", "搜图",
    description = "搜索指令"
), PixivHelperLogger {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private const val MIN_SIMILARITY = 0.85

    private const val MAX_REPEAT = 10

    private suspend fun search(url: String, repeat: Int = 0): List<ImageSearcher.SearchResult> = runCatching {
        ImageSearcher.getSearchResults(url)
    }.onFailure {
        logger.warning("搜索[$url]第${repeat}次失败", it)
        if (repeat >= MAX_REPEAT) {
            throw IllegalStateException("搜索次数超过${MAX_REPEAT}", it)
        }
    }.getOrElse { search(url, repeat + 1) }

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(image: Image) = runCatching {
        search(image.queryUrl()).maxByOrNull {
            it.similarity
        }?.let { result ->
            if (result.similarity > MIN_SIMILARITY) getHelper().runCatching {
                launch {
                    logger.verbose("相似度大于${MIN_SIMILARITY}开始获取搜索结果${result}")
                    getImages(getIllustInfo(result.pid))
                }
            }
            buildString {
                appendLine("相似度: ${result.similarity * 100}%")
                appendLine(result.content + "#${result.uid}")
            }
        }
    }.onSuccess { result ->
        quoteReply(result ?: "没有搜索结果")
    }.onFailure {
        logger.verbose("搜索失败$image", it)
        quoteReply("搜索失败， ${it.message}")
    }.isSuccess
}