package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import xyz.cssxsh.mirai.plugin.*

object PixivArticleCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "article", "特辑",
    description = "PIXIV特辑指令"
) {

    @Handler
    suspend fun CommandSenderOnMessage<*>.load() = withHelper {
        articlesRandom().let { data ->
            data.articles.forEach {
                addCacheJob(name = "ARTICLE[${it.aid}]", reply = false) { getArticle(article = it).eros() }
            }
            buildMessageByArticle(data = data)
        }
    }
}