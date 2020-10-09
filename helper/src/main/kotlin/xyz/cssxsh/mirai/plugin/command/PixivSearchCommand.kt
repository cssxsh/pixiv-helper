package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.description.CommandArgumentParser
import net.mamoe.mirai.console.command.description.CommandArgumentParserException
import net.mamoe.mirai.console.command.description.buildCommandArgumentContext
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.plugin.*

@Suppress("unused")
object PixivSearchCommand : SimpleCommand(
    PixivHelperPlugin,
    "search", "搜索",
    description = "缓存指令",
    prefixOptional = true,
    overrideContext = buildCommandArgumentContext {
        Image::class with object : CommandArgumentParser<Image> {
            override fun parse(raw: String, sender: CommandSender): Image = throw CommandArgumentParserException(raw)

            override fun parse(raw: MessageContent, sender: CommandSender): Image = when(raw) {
                is Image ->  raw
                else -> throw CommandArgumentParserException(raw.content)
            }
        }
    }
) {

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(image: Image) = runCatching {
        ImageSearcher.getSearchResults(image.queryUrl()).maxByOrNull { it.similarity }?.let {
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