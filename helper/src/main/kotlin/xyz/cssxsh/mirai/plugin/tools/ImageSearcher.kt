package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.tool.*

object ImageSearcher : HtmlParser(ignore = ignore(name = "Search")) {

    private const val API = "https://saucenao.com/search.php"

    private const val PIXIV_INDEX = 5 // Index #5: pixiv Images

    private const val DANBOORU_INDEX = "9"

    private const val GELBOORU_INDEX = "35"

    override fun client() = HttpClient(OkHttp) {
        engine {
            config {
                sslSocketFactory(RubySSLSocketFactory, RubyX509TrustManager)
                hostnameVerifier { _, _ -> true }
            }
        }
    }

    private val pixiv: (Document) ->  List<SearchResult> = {
        it.select(".resulttablecontent").map { content ->
            SearchResult(
                similarity = content.select(".resultsimilarityinfo")
                    .text().replace("%", "").toDouble() / 100,
                pid = content.select(".resultcontent a.linkify")
                    .first().text().toLong(),
                title = content.select(".resultcontent")
                    .text().substringBeforeLast("Pixiv").trim(),
                uid = content.select(".resultcontent a.linkify")
                    .last().attr("href").toHttpUrl().queryParameter("id")!!.toLong(),
                name = content.select(".resultcontent")
                    .text().substringAfterLast(":").trim()
            )
        }
    }

    suspend fun getSearchResults(url: String): List<SearchResult> = html(pixiv) {
        url(API)
        method = HttpMethod.Get
        parameter("db", PIXIV_INDEX)
        parameter("url", url)
    }

    suspend fun postSearchResults(file: ByteArray): List<SearchResult> = html(pixiv) {
        url(API)
        method = HttpMethod.Post
        body = MultiPartFormDataContent(formData {
            append("database", PIXIV_INDEX)
            append("file", "file.jpg") {
                writeFully(file)
            }
        })
    }

    private val MD5 = """[0-9a-f]{32}""".toRegex()

    private val image: (String) -> String = { md5 ->
        "https://img1.gelbooru.com/images/${md5.substring(0..1)}/${md5.substring(2..3)}/${md5}.jpg"
    }

    private val twitter: (Document) ->  List<TwitterImage> = {
        it.select(".resulttable").mapNotNull { content ->
            if ("Twitter" in content.text()) {
                TwitterImage(
                    similarity = content.select(".resulttablecontent .resultsimilarityinfo")
                        .text().replace("%", "").toDouble() / 100,
                    tweet = content.select(".resulttablecontent .resultcontent a")
                        .first().attr("href"),
                    image = content.select(".resulttableimage").findAll(MD5).first().value.let(image)
                )
            } else {
                null
            }
        }
    }

    suspend fun getTwitterImage(url: String): List<TwitterImage> = html(twitter) {
        url(API)
        method = HttpMethod.Get
        parameter("url", url)
        parameter("dbs[]", DANBOORU_INDEX)
        parameter("dbs[]", GELBOORU_INDEX)
    }
}