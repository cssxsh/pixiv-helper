package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.request.*
import io.ktor.http.*
import org.jsoup.*
import org.jsoup.nodes.*
import org.jsoup.safety.*
import xyz.cssxsh.mirai.plugin.model.*
import java.util.*

object Pixivision : HtmlParser(name = "Pixivision") {

    private const val API = "https://www.pixivision.net/"

    private val settings = Document.OutputSettings().prettyPrint(false)

    private fun Element.doc(): String {
        return Jsoup.clean(html(), "", Whitelist.none(), settings)
    }

    private val article: (Document) -> PixivArticle = { document ->
        PixivArticle(
            title = document.select(".am__title").text(),
            description = document.select(".am__description").first().doc(),
            illusts = document.select(".am__work").map { element ->
                PixivArticle.Illust(
                    pid = element.select(".am__work__title a").first()
                        .href().substringAfterLast("/").toLong(),
                    title = element.select(".am__work__title a").first()
                        .text(),
                    uid = element.select(".am__work__user-name a").first()
                        .href().substringAfterLast("/").toLong(),
                    name = element.select(".am__work__user-name a").first()
                        .text()
                )
            }
        )
    }

    suspend fun getArticle(aid: Long, locale: Locale = Locale.CHINA) = html(article) {
        url(Url(API).copy(encodedPath = "/${locale.language}/a/${aid}"))
        method = HttpMethod.Get
        header(HttpHeaders.AcceptLanguage, locale.language)
        header(HttpHeaders.Referrer, url)
    }
}