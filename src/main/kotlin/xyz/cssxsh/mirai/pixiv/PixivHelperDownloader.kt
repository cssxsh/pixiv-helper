package xyz.cssxsh.mirai.pixiv

import io.ktor.http.*
import kotlinx.coroutines.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.tool.*
import java.io.IOException
import java.net.*

public object PixivHelperDownloader : PixivDownloader(host = PIXIV_HOST, async = PIXIV_DOWNLOAD_ASYNC) {

    private var erros = 0

    override val ignore: suspend (Throwable) -> Boolean = { throwable ->
        when (throwable) {
            is IOException -> {
                logger.warning { "Pixiv Download 错误, 已忽略: $throwable" }
                delay(++erros * 1000L)
                erros--
                true
            }
            else -> false
        }
    }

    override val timeout: Long get() = TimeoutDownload

    override val blockSize: Int get() = BlockSize

    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    override val proxy: Proxy? by lazy {
        if (ProxyDownload.isNotBlank()) {
            Url(ProxyDownload).toProxy()
        } else {
            null
        }
    }

    override suspend fun <R> downloadImageUrls(
        urls: List<Url>,
        block: suspend (url: Url, deferred: Deferred<ByteArray>) -> R
    ): List<R> {
        val downloads = if (ProxyMirror.isNotBlank()) {
            urls.map { url ->
                if (url.host == "i.pximg.net") {
                    URLBuilder(url).apply { host = ProxyMirror }.build()
                } else {
                    url
                }
            }
        } else {
            urls
        }
        return super.downloadImageUrls(downloads, block)
    }
}