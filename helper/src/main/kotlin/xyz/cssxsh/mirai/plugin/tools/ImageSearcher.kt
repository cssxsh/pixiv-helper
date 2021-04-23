package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.utils.io.core.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import xyz.cssxsh.pixiv.model.SearchResult
import xyz.cssxsh.pixiv.tool.RubySSLSocketFactory
import xyz.cssxsh.pixiv.tool.RubyX509TrustManager
import kotlin.io.use

object ImageSearcher {

    private const val API = "https://saucenao.com/search.php"

    private const val PIXIV_INDEX = 5 // Index #5: pixiv Images

    private const val DANBOORU_INDEX = "9"

    private const val GELBOORU_INDEX = "35"

    data class TwitterImage(
        val similarity: Double,
        val tweet: String,
        val image: String
    )

    private fun HttpClient() = HttpClient(OkHttp) { engine {
        config {
            sslSocketFactory(RubySSLSocketFactory, RubyX509TrustManager)
            hostnameVerifier { _, _ -> true }
        }
    }}

    private suspend fun <R> useHttpClient(
        ignore: suspend (Throwable) -> Boolean,
        block: suspend (HttpClient) -> R,
    ): R = HttpClient().use { client ->
        runCatching {
            block(client)
        }.getOrElse { throwable ->
            if (ignore(throwable)) {
                useHttpClient(ignore = ignore, block = block)
            } else {
                throw throwable
            }
        }
    }

    private fun String.parseSearchResult(): List<SearchResult> {
        return Jsoup.parse(this).select(".resulttablecontent").map { content ->
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

    suspend fun getSearchResults(ignore: suspend (Throwable) -> Boolean, url: String) = useHttpClient(ignore) { client ->
        client.get<String>(API) {
            parameter("db", PIXIV_INDEX)
            parameter("url", url)
        }
    }.parseSearchResult()

    suspend fun postSearchResults(ignore: suspend (Throwable) -> Boolean, file: ByteArray) = useHttpClient(ignore) { client ->
        client.post<String>(API) {
            body = MultiPartFormDataContent(formData {
                append("database", PIXIV_INDEX)
                append("file", "file.jpg") {
                    writeFully(file)
                }
            })
        }
    }.parseSearchResult()

    private val MD5 = """[0-9a-f]{32}""".toRegex()

    private fun Elements.getGelbooruImage() = MD5.find(html())!!.value.let {
        "https://img1.gelbooru.com/images/${it.substring(0..1)}/${it.substring(2..3)}/${it}.jpg"
    }

    private fun String.parseTwitterImage(): List<TwitterImage> {
        return Jsoup.parse(this).select(".resulttable").mapNotNull { content ->
            if ("Twitter" in content.text()) {
                TwitterImage(
                    similarity = content.select(".resulttablecontent .resultsimilarityinfo")
                        .text().replace("%", "").toDouble() / 100,
                    tweet = content.select(".resulttablecontent .resultcontent a")
                        .first().attr("href"),
                    image = content.select(".resulttableimage").getGelbooruImage()
                )
            } else {
                null
            }
        }
    }

    suspend fun getTwitterImage(ignore: suspend (Throwable) -> Boolean, url: String) = useHttpClient(ignore) { client ->
        client.get<String>(API) {
            parameter("url", url)
            parameter("dbs[]", DANBOORU_INDEX)
            parameter("dbs[]", GELBOORU_INDEX)
        }
    }.parseTwitterImage()
}