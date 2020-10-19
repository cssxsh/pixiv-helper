package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.plugin.*

@Suppress("unused")
object PixivSearchCommand : SimpleCommand(
    PixivHelperPlugin,
    "search", "搜索", "搜图",
    description = "搜索指令",
    prefixOptional = true
), PixivHelperLogger {

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(image: Image) = runCatching {
        ImageSearcher.postSearchResults(image.queryUrl()).maxByOrNull {
            it.similarity
        }?.let {
            if (it.similarity > 0.9) getHelper().runCatching {
                launch {
                    logger.verbose("开始获取搜索结果${it.pid}")
                    getImages(getIllustInfo(it.pid))
                }
            }
            "相似度: ${it.similarity * 100}% \n ${it.content}"
        }
    }.onSuccess { result ->
        quoteReply(result ?: "没有搜索结果")
    }.onFailure {
        quoteReply("搜索失败， ${it.message}")
    }.isSuccess
}