package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import net.mamoe.mirai.utils.warning
import xyz.cssxsh.pixiv.tool.PixivDownloader

object PixivHelperDownloader : PixivDownloader(
    initHost = PIXIV_HOST,
    ignore = PixivDownloadIgnore
) {

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