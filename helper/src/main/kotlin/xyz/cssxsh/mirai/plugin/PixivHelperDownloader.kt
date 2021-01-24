package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import okhttp3.internal.http2.StreamResetException
import xyz.cssxsh.pixiv.tool.PixivDownloader
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object PixivHelperDownloader : PixivDownloader(
    host = mapOf(
        "i.pximg.net" to (134..147).map {
            "210.140.92.${it}"
        }
    ),
    ignore = { _, throwable, _ ->
        when (throwable) {
            is SSLException,
            is EOFException,
            is ConnectException,
            is SocketTimeoutException,
            is HttpRequestTimeoutException,
            is StreamResetException,
            is ClosedReceiveChannelException,
            is NullPointerException,
            is UnknownHostException,
            -> {
                true
            }
            else -> when {
                throwable.message?.contains("Required SETTINGS preface not received") == true -> {
                    true
                }
                throwable.message?.contains("Completed read overflow") == true -> {
                    true
                }
                else -> false
            }
        }
    }
) {
    suspend fun downloadImageUrls(urls: List<String>, dir: File): List<Result<File>> = downloadImageUrls(
        urls = urls,
        block = { _, url, result ->
            runCatching {
                dir.resolve(Url(url).getFilename()).apply {
                    writeBytes(result.getOrThrow())
                }
            }
        }
    )
}