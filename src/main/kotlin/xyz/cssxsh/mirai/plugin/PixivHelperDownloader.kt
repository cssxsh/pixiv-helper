package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import kotlinx.coroutines.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.tool.*
import java.net.*

object PixivHelperDownloader : PixivDownloader(host = PIXIV_HOST, async = PIXIV_DOWNLOAD_ASYNC) {

    override val ignore: Ignore get() = PixivDownloadIgnore

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
            urls.map { if (it.host == "i.pximg.net") it.copy(host = ProxyMirror) else it }
        } else {
            urls
        }
        return super.downloadImageUrls(downloads, block)
    }
}