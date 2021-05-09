package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.*
import okio.ByteString.Companion.toByteString
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.tools.ImageSearcher

object PixivSearchCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "search", "搜索", "搜图",
    description = "PIXIV搜索指令，通过https://saucenao.com/"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private fun MessageChain.getQuoteImage(): Image {
        return requireNotNull(findIsInstance<QuoteReply>()?.let { PixivHelperListener.images[it.source.metadata()] }) { "找不到图片" }
    }

    private fun CommandSenderOnMessage<*>.findTwitterImage(url: String) = launch {
        ImageSearcher.getTwitterImage(url = url).maxByOrNull { it.similarity }?.let {
            quoteReply(buildMessageChain {
                appendLine("推特图源")
                appendLine("相似度: ${it.similarity}")
                appendLine("Tweet: ${it.tweet}")
                appendLine("原图: ${it.image}")
            })
        }
    }

    private suspend fun search(url: String) = ImageSearcher.getSearchResults(url = url).maxByOrNull { it.similarity }

    @Handler
    suspend fun CommandSenderOnMessage<*>.search(image: Image = fromEvent.message.getQuoteImage()) = withHelper {
        logger.info { "搜索 ${image.queryUrl()}" }
        requireNotNull(image.findSearchResult() ?: search(image.queryUrl())) {
            findTwitterImage(url = image.queryUrl())
            "没有PIXIV搜索结果"
        }.also { result ->
            if (result.similarity > MIN_SIMILARITY) {
                result.copy(md5 = image.md5.toByteString().hex()).save()
            } else {
                findTwitterImage(url = image.queryUrl())
            }
        }.getContent()
    }
}