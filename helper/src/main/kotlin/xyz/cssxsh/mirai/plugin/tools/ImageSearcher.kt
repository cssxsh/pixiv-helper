package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.utils.io.core.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import xyz.cssxsh.pixiv.data.SearchResult
import kotlin.io.use

@Suppress("unused")
object ImageSearcher {
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

    private suspend fun <R> useHttpClient(
        ignore: suspend (Throwable) -> Boolean,
        block: suspend (HttpClient) -> R,
    ): R = httpClient.use { client ->
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

    private fun parse(html: String): List<SearchResult> =
        Jsoup.parse(html).select(".resulttablecontent").map { content ->
            SearchResult(
                similarity = content.select(".resultsimilarityinfo")
                    .text().replace("%", "").toDouble() / 100,
                content = content.select(".resultcontent")
                    .text(),
                pid = content.select(".resultcontent a.linkify")
                    .first().text().toLong(),
                uid = content.select(".resultcontent a.linkify")
                    .last().attr("href").toHttpUrl().queryParameter("id")!!.toLong()
            )
        }

    suspend fun getSearchResults(
        ignore: suspend (Throwable) -> Boolean = { _ -> false },
        url: String,
    ): List<SearchResult> = useHttpClient(ignore) { client ->
        client.get<String>(API) {
            parameter("db", DB_INDEX)
            parameter("url", url)
        }
    }.let { html -> parse(html) }

    suspend fun postSearchResults(
        ignore: suspend (Throwable) -> Boolean = { _ -> false },
        file: ByteArray,
    ): List<SearchResult> = useHttpClient(ignore) { client ->
        client.post<String>(API) {
            body = MultiPartFormDataContent(formData {
                append("database", DB_INDEX)
                append("file", "file.jpg") {
                    writeFully(file)
                }
            })
        }
    }.let { html -> parse(html) }
}