package mirai.tools

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.compression.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.utils.io.core.*
import mirai.data.BiliAccInfo
import mirai.data.BiliSearchResult

object Bilibili {
    private const val SEARCH_URL = "https://api.bilibili.com/x/space/arc/search"
    private const val ROOM_INFO = "http://api.live.bilibili.com/room/v1/Room/getRoomInfoOld"
    private const val ACC_INFO = "https://api.bilibili.com/x/space/acc/info"

    private suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = HttpClient(OkHttp) {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 60_000
            connectTimeoutMillis = 60_000
            requestTimeoutMillis = 180_000
        }
        BrowserUserAgent()
        ContentEncoding {
            gzip()
            deflate()
            identity()
        }
    }.use { block(it) }

    suspend fun searchVideo(
        uid: Long,
        pageSize: Int = 30,
        pageNum: Int = 1
    ): BiliSearchResult = useHttpClient { client ->
        client.get(SEARCH_URL) {
            parameter("mid", uid)
            parameter("keyword", "")
            parameter("order", "pubdate")
            parameter("jsonp", "jsonp")
            parameter("ps", pageSize)
            parameter("pn", pageNum)
            parameter("tid", 0)
        }
    }

    suspend fun accInfo(
        uid: Long
    ): BiliAccInfo = useHttpClient { client ->
        client.get(ACC_INFO) {
            parameter("mid", uid)
            parameter("jsonp", "jsonp")
            parameter("tid", 0)
        }
    }

    suspend fun getPic(url: String): ByteArray = useHttpClient { client ->
        client.get(url)
    }
}