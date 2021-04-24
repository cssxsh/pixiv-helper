package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import net.mamoe.mirai.utils.warning
import xyz.cssxsh.pixiv.tool.PixivDownloader
import java.io.File

object PixivHelperDownloader : PixivDownloader(
    initHost = PIXIV_HOST,
    ignore = PixivDownloadIgnore
) {

    suspend fun downloadImages(urls: List<Url>, dir: File): List<Result<File>> = downloadImageUrls(
        urls = urls,
        block = { url, result ->
            result.mapCatching {
                dir.resolve(url.filename).apply {
                    writeBytes(it)
                }
            }.onFailure {
                if (it.isNotCancellationException()) {
                    logger.warning({ "[$url]下载失败" }, it)
                }
            }
        }
    )
}