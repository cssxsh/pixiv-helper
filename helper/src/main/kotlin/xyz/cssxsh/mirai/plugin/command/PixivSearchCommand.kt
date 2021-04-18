package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivSearchData
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.command.PixivSearchCommand.getQuoteImage
import xyz.cssxsh.mirai.plugin.tools.ImageSearcher

@Suppress("unused")
object PixivSearchCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "search", "搜索", "搜图",
    description = "PIXIV搜索指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private fun MessageChain.getQuoteImage(): Image = requireNotNull(findIsInstance<QuoteReply>()?.let { quote ->
        PixivHelperListener.images.entries.find { (source, _) -> source.toString() == quote.source.toString() }
    }) { "找不到图片" }.value

    @Handler
    suspend fun CommandSenderOnMessage<*>.search(image: Image = fromEvent.message.getQuoteImage()) = runCatching {
        logger.info { "搜索 ${image.queryUrl()}" }
        PixivSearchData.results.getOrElse(image.getMd5Hex()) {
            ImageSearcher.getSearchResults(
                ignore = SearchApiIgnore,
                url = image.queryUrl().replace("http:", "https:")
            ).run {
                requireNotNull(maxByOrNull { it.similarity }) { "没有搜索结果" }
            }
        }.also { result ->
            if (result.similarity > MIN_SIMILARITY) {
                PixivSearchData.results[image.getMd5Hex()] = result
            }
        }
    }.onSuccess { result ->
        quoteReply(buildString {
            appendLine("相似度: ${result.similarity * 100}%")
            appendLine(result.content + "#${result.uid}")
        })
    }.onFailure {
        logger.verbose({ "搜索失败$image" }, it)
        quoteReply("搜索失败， ${it.message}")
    }.isSuccess
}