package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.tools.ImageSearcher

@Suppress("unused")
object PixivSearchCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "search", "搜索", "搜图",
    description = "搜索指令",
    prefixOptional = true
), PixivHelperLogger {

    private const val MIN_SIMILARITY = 0.85

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(image: Image) = runCatching {
        ImageSearcher.getSearchResults(image.queryUrl()).maxByOrNull {
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