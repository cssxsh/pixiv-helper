package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.utils.io.core.*
import net.mamoe.mirai.utils.warning
import org.jsoup.Jsoup
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.pixiv.data.SearchResult
import kotlin.io.use

@Suppress("unused")
object ImageSearcher : PixivHelperLogger {
    private const val API = "https://saucenao.com/search.php"
    private const val DB_INDEX = 5 // Index #5: pixiv Images
    private val httpClient: HttpClient
        get() = HttpClient(OkHttp) {
            install(HttpTimeout) {
                socketTimeoutMillis = 60_000
                connectTimeoutMillis = 60_000
                requestTimeoutMillis = 60_000
            }
        }

    private fun parse(html: String): List<SearchResult> =
        Jsoup.parse(html).select(".resulttablecontent").map { content ->
            SearchResult(
                similarity = content.select(".resultsimilarityinfo")
                    .text().replace("%", "").toDouble() / 100,
                content = content.select(".resultcontent").text(),
                pid = content.select(".resultcontent a.linkify").first().text().toLong(),
                uid = content.select(".resultcontent a.linkify").last().attr("href").run {
                    substring(lastIndexOf("=") + 1).toLong()
                }
            )
        }

    suspend fun getSearchResults(
        picUrl: String
    ): List<SearchResult> = httpClient.use { client ->
        client.get<String>(API) {
            parameter("db", DB_INDEX)
            parameter("url", picUrl)
        }.let { html ->
            parse(html)
        }
    }

    suspend fun postSearchResults(
        picUrl: String
    ): List<SearchResult> = httpClient.use { client ->
        runCatching {
            client.get<ByteArray>(picUrl.replace("http", "https"))
        }.onFailure {
            logger.warning({ "图片下载失败, $picUrl" }, it)
        }.getOrThrow().let {
            client.post<String>(API) {
                body = MultiPartFormDataContent(formData {
                    append("database", DB_INDEX)
                    append("file", "file.jpg") {
                        writeFully(it)
                    }
                })
            }
        }.let { html ->
            parse(html)
        }
    }
}