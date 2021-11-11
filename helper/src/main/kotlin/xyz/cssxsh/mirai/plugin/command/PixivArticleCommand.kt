package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import xyz.cssxsh.mirai.plugin.*

object PixivArticleCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "article", "特辑",
    description = "PIXIV特辑指令"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    @Handler
    suspend fun CommandSenderOnMessage<*>.load() = withHelper {
        buildMessageByArticle(articles = randomArticles().onEach { article ->
            addCacheJob(name = "ARTICLE[${article.aid}]", reply = false) { getArticle(article = article).eros() }
        })
    }
}