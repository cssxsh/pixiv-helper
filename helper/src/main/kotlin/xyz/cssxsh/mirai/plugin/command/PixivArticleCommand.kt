package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import xyz.cssxsh.mirai.plugin.*

object PixivArticleCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "article", "特辑",
    description = "PIXIV特辑指令"
) {

    override val prefixOptional: Boolean = true

    @Handler
    suspend fun CommandSenderOnMessage<*>.load() = withHelper {
        buildMessageByArticle(data = randomArticles().apply {
            for (article in articles) {
                addCacheJob(name = "ARTICLE[${article.aid}]", reply = false) { getArticle(article = article).eros() }
            }
        })
    }
}