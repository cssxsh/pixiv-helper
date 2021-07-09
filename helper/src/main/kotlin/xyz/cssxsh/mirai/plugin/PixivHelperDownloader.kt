package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.pixiv.tool.*

object PixivHelperDownloader : PixivDownloader(host = PIXIV_HOST) {

    override val ignore: suspend (Throwable) -> Boolean = PixivDownloadIgnore

    suspend fun downloadImage(url: Url): ByteArray = downloadImageUrls(
        urls = listOf(url),
        block = { _, result ->
            result.onFailure {
                if (it.isNotCancellationException()) {
                    logger.warning({ "[$url]下载失败" }, it)
                }
            }.getOrThrow()
        }
    ).single()
}