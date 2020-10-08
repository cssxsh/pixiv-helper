package xyz.cssxsh.mirai.plugin

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import org.jsoup.Jsoup

object ImageSearcher: PixivHelperLogger {
    private const val API = "https://saucenao.com/search.php"
    private const val DB_INDEX = 5 // Index #5: pixiv Images
    private val httpClient: HttpClient = HttpClient {
        install(HttpTimeout) {
            socketTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
    }

    suspend fun getSearchResults(picUrl: String): List<SearchResult> = httpClient.get<String>(API) {
        parameter("db", DB_INDEX)
        parameter("url", picUrl)
    }.let { html ->
        logger.verbose("图片 $picUrl 查询")
        Jsoup.parse(html).select(".resulttablecontent").map {
            SearchResult(
                similarity = it.select(".resultsimilarityinfo")
                    .text().replace("%", "").toDouble() / 100,
                content = it.select(".resultcontent").text(),
                pid = it.select(".resultcontent a").first().text().toLong()
            )
        }
    }

    suspend fun postSearchResults(file: ByteArray): List<SearchResult> = httpClient.post(API) {
        body = MultiPartFormDataContent(formData {
            append("file", file)
            append("database", DB_INDEX)
        })
    }

    class SearchResult(
        val similarity: Double,
        val content: String,
        val pid: Long
    )
}