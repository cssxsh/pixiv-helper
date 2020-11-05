package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivSearchData
import xyz.cssxsh.mirai.plugin.data.SearchResult
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

    private suspend fun search(url: String, repeat: Int = 0): List<SearchResult> = runCatching {
        ImageSearcher.getSearchResults(url)
    }.onFailure {
        logger.warning("搜索[$url]第${repeat}次失败", it)
        if (repeat >= MAX_REPEAT) {
            throw IllegalStateException("搜索次数超过${MAX_REPEAT}", it)
        }
    }.getOrElse { search(url, repeat + 1) }

    @ExperimentalUnsignedTypes
    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(image: Image) = runCatching {
        PixivSearchData.resultMap.getOrPut(image.md5.joinToString("") { it.toUByte().toString(16) }) {
            search(image.queryUrl()).maxByOrNull {
                it.similarity
            }?.takeIf { result ->
                result.similarity > MIN_SIMILARITY
            }?.also { result ->
                getHelper().runCatching {
                    launch {
                        logger.verbose("相似度大于${MIN_SIMILARITY}开始获取搜索结果${result}")
                        getImages(getIllustInfo(result.pid))
                    }
                }
            }.let {
                requireNotNull(it) { "没有搜索结果" }
            }
        }
    }.onSuccess { result ->
        quoteReply(buildString {
            appendLine("相似度: ${result.similarity * 100}%")
            appendLine(result.content + "#${result.uid}")
        })
    }.onFailure {
        logger.verbose("搜索失败$image", it)
        quoteReply("搜索失败， ${it.message}")
    }.isSuccess
}