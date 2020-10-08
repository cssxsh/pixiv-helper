package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.description.CommandArgumentParser
import net.mamoe.mirai.console.command.description.CommandArgumentParserException
import net.mamoe.mirai.console.command.description.buildCommandArgumentContext
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.queryUrl
import xyz.cssxsh.mirai.plugin.ImageSearcher
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin

@Suppress("unused")
object PixivSearch : SimpleCommand(
    PixivHelperPlugin,
    "search", "搜索",
    description = "缓存指令",
    prefixOptional = true,
    overrideContext = buildCommandArgumentContext {
        Image::class with object : CommandArgumentParser<Image> {
            override fun parse(raw: String, sender: CommandSender): Image =
                """[A-Z\-]+""".toRegex().find(raw)?.let { matchResult ->
                    Image(matchResult.value)
                } ?: throw CommandArgumentParserException()
        }
    }
) {

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(image: Image) = runCatching {
        ImageSearcher.getSearchResults(image.queryUrl()).maxByOrNull { it.similarity }
    }.onSuccess { result ->
        quoteReply(result?.content ?: "没有搜索结果")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}