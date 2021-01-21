package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import net.mamoe.mirai.utils.*
import okhttp3.internal.http2.StreamResetException
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.imagesFolder
import xyz.cssxsh.pixiv.tool.PixivDownloader
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object PixivHelperDownloader : PixivDownloader(
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
                dir.resolve(url.getFilename()).apply {
                    writeBytes(result.getOrThrow())
                }
            }
        }
    )

    suspend fun getImages(
        pid: Long,
        urls: List<String>,
    ): List<File> = imagesFolder(pid).let { dir ->
        urls.filter { dir.resolve(it.getFilename()).exists().not() }.takeIf { it.isNotEmpty() }?.let { downloads ->
            dir.mkdirs()
            downloadImageUrls(downloads) { _, url, result ->
                runCatching {
                    dir.resolve(url.getFilename()).apply {
                        writeBytes(result.getOrThrow())
                    }
                }.onFailure {
                    logger.warning({ "作品(${pid})下载错误" }, it)
                }.exceptionOrNull()
            }.mapNotNull { it }.let {
                check(it.isEmpty()) { "作品(${pid})下载错误, ${it.first()}" }
            }
            logger.info { "作品(${pid}){${downloads.size}}下载完成" }
        }
        urls.map { url ->
            dir.resolve(url.getFilename())
        }
    }
}