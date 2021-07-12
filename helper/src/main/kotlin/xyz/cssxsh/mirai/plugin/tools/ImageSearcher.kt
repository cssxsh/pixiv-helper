package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.tool.*

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
object ImageSearcher : HtmlParser(name = "Search") {

    init {
        RubySSLSocketFactory.regexes.add("""saucenao\.com""".toRegex())
    }

    private const val API = "https://saucenao.com/search.php"

    private const val PIXIV_INDEX = 5 // Index #5: pixiv Images

    private const val ALL_INDEX = 999

    val key by ImageSearchConfig::key

    override val client = super.client.config {
        Json {
            serializer = KotlinxSerializer(PixivJson)
        }
        engine {
            (this as OkHttpConfig).config {
                sslSocketFactory(RubySSLSocketFactory, RubyX509TrustManager)
                hostnameVerifier { _, _ -> true }
            }
        }
    }

    private val pixiv: (Document) -> List<PixivSearchResult> = {
        it.select(".resulttablecontent").map { content ->
            PixivSearchResult(
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

    suspend fun pixiv(url: String): List<PixivSearchResult> = html(pixiv) {
        url(API)
        method = HttpMethod.Get
        parameter("db", PIXIV_INDEX)
        parameter("url", url)
    }

    private val MD5 = """[0-9a-f]{32}""".toRegex()

    private val image: (String) -> String = { md5 ->
        "https://img1.gelbooru.com/images/${md5.substring(0..1)}/${md5.substring(2..3)}/${md5}.jpg"
    }

    private val other: (Document) -> List<SearchResult> = {
        it.select(".resulttable").mapNotNull { content ->
            if ("Twitter" in content.text()) {
                TwitterSearchResult(
                    similarity = content.select(".resulttablecontent .resultsimilarityinfo")
                        .text().replace("%", "").toDouble() / 100,
                    tweet = content.select(".resulttablecontent .resultcontent a")
                        .first().attr("href"),
                    image = content.select(".resulttableimage").findAll(MD5).first().value.let(image)
                )
            } else {
                OtherSearchResult(
                    similarity = content.select(".resulttablecontent .resultsimilarityinfo")
                        .text().replace("%", "").toDouble() / 100,
                    text = content.wholeText()
                )
            }
        }
    }

    suspend fun other(url: String): List<SearchResult> = html(other) {
        url(API)
        method = HttpMethod.Get
        parameter("url", url)
        parameter("dbs[]", ALL_INDEX)
    }

    private fun JsonSearchResults.decode(): List<SearchResult> {
        return results.map {
            when {
                "pixiv_id" in it.data -> {
                    PixivSearchResult(
                        similarity = it.info.similarity / 100,
                        pid = it.data.getValue("pixiv_id").jsonPrimitive.long,
                        title = it.data.getValue("title").jsonPrimitive.content,
                        uid = it.data.getValue("member_id").jsonPrimitive.long,
                        name = it.data.getValue("member_name").jsonPrimitive.content
                    )
                }
                "twitter.com" in it.data["source"].toString() -> {
                    TwitterSearchResult(
                        similarity = it.info.similarity,
                        tweet = it.data.getValue("source").jsonPrimitive.content,
                        image = MD5.matchEntire(it.info.indexName)!!.value.let(image)
                    )
                }
                else -> {
                    OtherSearchResult(
                        similarity = it.info.similarity,
                        text = it.data.toString()
                    )
                }
            }
        }
    }

    suspend fun json(url: String): List<SearchResult> {
        return client.get<JsonSearchResults>(API) {
            parameter("url", url)
            parameter("output_type", 2)
            parameter("api_key", key)
            // parameter("testmode", )
            // parameter("dbmask", )
            // parameter("dbmaski", )
            parameter("db", ALL_INDEX)
            // parameter("numres", )
            // parameter("dedupe", )
        }.decode()
    }
}