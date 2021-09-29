package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import kotlinx.coroutines.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.tool.*
import java.net.*

object PixivHelperDownloader : PixivDownloader(host = PIXIV_HOST, async = 32) {

    override val ignore: suspend (Throwable) -> Boolean get() = PixivDownloadIgnore

    override val timeout: Long by PixivHelperSettings::timeoutDownload

    override val blockSize: Int by PixivHelperSettings::blockSize

    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    override val proxy: Proxy? by lazy {
        if (PixivHelperSettings.proxyDownload.isNotBlank()) {
            Url(PixivHelperSettings.proxyDownload).toProxy()
        } else {
            null
        }
    }

    override suspend fun <R> downloadImageUrls(
        urls: List<Url>,
        block: suspend (url: Url, deferred: Deferred<ByteArray>) -> R
    ): List<R> {
        val proxy = PixivHelperSettings.pximg
        return if (proxy.isNotBlank()) {
            super.downloadImageUrls(urls.map { if (it.host == "i.pximg.net") it.copy(host = proxy) else it }, block)
        } else {
            super.downloadImageUrls(urls, block)
        }
    }
}