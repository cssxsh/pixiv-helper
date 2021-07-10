package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.tool.*

object PixivHelperDownloader : PixivDownloader(host = PIXIV_HOST, timeout = 30 * 1000L) {

    override val ignore: suspend (Throwable) -> Boolean get() = PixivDownloadIgnore

    override suspend fun <R> downloadImageUrls(
        urls: List<Url>,
        block: (url: Url, result: Result<ByteArray>) -> R
    ): List<R> {
        return if (PixivHelperSettings.pximg.isNotBlank()) {
            val proxy = PixivHelperSettings.pximg
            super.downloadImageUrls(urls.map { if (it.host == "i.pximg.net") it.copy(host = proxy) else it }, block)
        } else {
            super.downloadImageUrls(urls, block)
        }
    }
}