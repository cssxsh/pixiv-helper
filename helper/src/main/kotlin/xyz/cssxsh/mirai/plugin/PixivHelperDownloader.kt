package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import okhttp3.internal.http2.ConnectionShutdownException
import okhttp3.internal.http2.StreamResetException
import xyz.cssxsh.pixiv.tool.PixivDownloader
import java.io.EOFException
import java.io.File
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object PixivHelperDownloader : PixivDownloader(
    initHost = mapOf(
        "i.pximg.net" to (134..147).map {
            "210.140.92.${it}"
        }
    ),
    ignore = { _, throwable, _ ->
        when (throwable) {
            is SSLException,
            is EOFException,
            is SocketException,
            is SocketTimeoutException,
            is HttpRequestTimeoutException,
            is StreamResetException,
            is NullPointerException,
            is UnknownHostException,
            is ConnectionShutdownException,
            -> true
            else -> when {
                throwable.message?.contains("Required SETTINGS preface not received") == true -> true
                throwable.message?.contains("Completed read overflow") == true -> true
                throwable.message?.contains("""Expected \d+, actual \d+""".toRegex()) == true -> true
                throwable.message?.contains("closed") == true -> true
                else -> false
            }
        }
    }
) {
    suspend fun downloadImageUrls(urls: List<String>, dir: File): List<Result<File>> = downloadImageUrls(
        urls = urls,
        block = { url, result ->
            runCatching {
                dir.resolve(Url(url).getFilename()).apply {
                    writeBytes(result.getOrThrow())
                }
            }
        }
    )
}