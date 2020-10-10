package xyz.cssxsh.mirai.plugin

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.streams.*
import org.jsoup.Jsoup
import kotlin.io.use

@Suppress("unused")
object ImageSearcher: PixivHelperLogger {
    private const val API = "https://saucenao.com/search.php"
    private const val DB_INDEX = 5 // Index #5: pixiv Images
    private val httpClient: HttpClient get() = HttpClient {
        install(HttpTimeout) {
            socketTimeoutMillis = 60_000
            connectTimeoutMillis = 60_000
            requestTimeoutMillis = 60_000
        }
    }

    private fun parse(html: String): List<SearchResult> = Jsoup.parse(html).select(".resulttablecontent").map {
        SearchResult(
            similarity = it.select(".resultsimilarityinfo")
                .text().replace("%", "").toDouble() / 100,
            content = it.select(".resultcontent").text(),
            pid = it.select(".resultcontent a").first().text().toLong()
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
        postSearchResults(client.get<ByteArray>(picUrl))
    }

    suspend fun postSearchResults(
        file: ByteArray
    ): List<SearchResult> = httpClient.use { client ->
        client.post<String>(API) {
            body = MultiPartFormDataContent(formData {
                append("database", DB_INDEX)
                append("file", "file.jpg") {
                    writeFully(file)
                }
            })
        }
    }.let { html ->
        parse(html)
    }

    data class SearchResult(
        val similarity: Double,
        val content: String,
        val pid: Long
    )
}