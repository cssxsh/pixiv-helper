package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*

object ImageSearcher : HtmlParser(name = "Search") {

    init {
        if (System.getProperty("xyz.cssxsh.mirai.plugin.tools.saucenao", "${true}").toBoolean()) {
            sni("""saucenao\.com""".toRegex())
        }
    }

    private const val API = "https://saucenao.com/search.php"

    private const val ALL_INDEX = 999

    var key: String = ""

    override val client = super.client.config {
        Json {
            serializer = KotlinxSerializer(PixivJson)
        }
        defaultRequest {
            header(HttpHeaders.Accept, ContentType.Text.Html)
        }
    }

    private val MD5 = """[0-9a-f]{32}""".toRegex()

    private val BASE64 = """[\w-=]{15}\.[\w]{3}""".toRegex()

    private val ID = """\d{3,9}""".toRegex()

    private val image = { name: String ->
        when {
            MD5 in name -> {
                val md5 = MD5.find(name)!!.value
                "https://img1.gelbooru.com/images/${md5.substring(0..1)}/${md5.substring(2..3)}/${md5}.jpg" to md5
            }
            BASE64 in name -> {
                val base64 = BASE64.find(name)!!.value
                "https://pbs.twimg.com/media/${base64}?name=orig" to ""
            }
            else -> {
                "无" to ""
            }
        }
    }

    private fun Element.similarity() = select(".resultsimilarityinfo").text().replace("%", "").toDouble() / 100

    private val other: (Document) -> List<SearchResult> = { document ->
        document.select(".resulttable").map { content ->
            val result = content.select(".resultcontent")
            val links = result.select("a")
            when {
                "Pixiv" in content.text() && result.select("a").isNotEmpty() -> {
                    PixivSearchResult(
                        similarity = content.similarity(),
                        pid = result.findAll(ID).first().value.toLong(),
                        title = result.text().substringBeforeLast("Pixiv ID:", "").trim(),
                        uid = links.last().href().let(::Url).parameters["id"]?.toLongOrNull() ?: 0,
                        name = result.text().substringAfterLast("Member:", "").trim()
                    )
                }
                "Twitter" in content.text() && links.isNotEmpty() -> {
                    val (image, md5) = content.select(".resulttableimage").html().let(image)
                    TwitterSearchResult(
                        similarity = content.similarity(),
                        tweet = links.first().href(),
                        image = image,
                        md5 = md5
                    )
                }
                else -> {
                    OtherSearchResult(
                        similarity = content.similarity(),
                        text = content.wholeText()
                    )
                }
            }
        }
    }

    suspend fun html(url: String): List<SearchResult> = html(other) {
        url(API)
        method = HttpMethod.Get
        parameter("url", url)
        parameter("dbs[]", ALL_INDEX)
    }

    private fun JsonSearchResults.decode(): List<SearchResult> {
        return results.orEmpty().map {
            val source = it.data["source"]?.jsonPrimitive?.content.orEmpty()
            when {
                "pixiv_id" in it.data -> {
                    PixivJson.decodeFromJsonElement<PixivSearchResult>(it.data)
                        .copy(similarity = it.info.similarity / 100)
                }
                "tweet_id" in it.data -> {
                    val (image, md5) = image(it.info.indexName)
                    TwitterSearchResult(
                        similarity = it.info.similarity / 100,
                        tweet = "https://twitter.com/detail/status/${it.data.getValue("tweet_id").jsonPrimitive.content}",
                        image = image,
                        md5 = md5
                    )
                }
                "i.pximg.net" in source || "www.pixiv.net" in source -> {
                    PixivSearchResult(
                        similarity = it.info.similarity / 100,
                        pid = source.substringAfterLast("/").substringAfterLast("=").toLong(),
                        title = it.info.indexName,
                        uid = 0,
                        name = ""
                    )
                }
                "pbs.twimg.com" in source || "twitter.com" in source -> {
                    val (image, md5) = image(it.info.indexName)
                    TwitterSearchResult(
                        similarity = it.info.similarity / 100,
                        tweet = source,
                        image = image,
                        md5 = md5
                    )
                }
                else -> {
                    OtherSearchResult(
                        similarity = it.info.similarity / 100,
                        text = it.data.entries.joinToString("\n") { (value, element) -> "$value: $element" }
                    )
                }
            }
        }
    }

    suspend fun json(url: String): List<SearchResult> = http { client ->
        client.get<JsonSearchResults>(API) {
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

    private val thumbnail = { hash: String ->
        "https://ascii2d.net/thumbnail/${hash[0]}/${hash[1]}/${hash[2]}/${hash[3]}/${hash}.jpg"
    }

    private val ascii2d: (Document) -> List<SearchResult> = { document ->
        document.select(".item-box").mapNotNull { content ->
            val small = content.select(".detail-box small").text()
            val link = content.select(".detail-box a").map { it.text() to it.href() }
            val hash = content.select(".hash").text()
            when (small) {
                "pixiv" -> {
                    PixivSearchResult(
                        similarity = Double.NaN,
                        pid = ID.find(link[0].second)!!.value.toLong(),
                        title = link[0].first,
                        uid = ID.find(link[1].second)!!.value.toLong(),
                        name = link[1].first
                    )
                }
                "twitter" -> {
                    TwitterSearchResult(
                        similarity = Double.NaN,
                        md5 = hash,
                        tweet = link[0].second,
                        image = thumbnail(hash)
                    )
                }
                else -> null
            }
        }
    }

    suspend fun ascii2d(url: String, bovw: Boolean): List<SearchResult> {
        val response: HttpResponse = http { client ->
            client.get("https://ascii2d.net/search/url/${url}")
        }

        val html: String = if (bovw) {
            http { client ->
                // https://ascii2d.net/search/color -> https://ascii2d.net/search/bovw
                client.get(response.request.url.toString().replace("color", "bovw"))
            }
        } else {
            response.receive()
        }

        return ascii2d(Jsoup.parse(html))
    }
}