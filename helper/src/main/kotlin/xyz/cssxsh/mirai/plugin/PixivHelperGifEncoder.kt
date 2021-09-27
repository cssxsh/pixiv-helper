package xyz.cssxsh.mirai.plugin

import com.squareup.gifencoder.*
import io.ktor.http.*
import kotlinx.coroutines.sync.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.tool.*
import java.io.*

object PixivHelperGifEncoder : PixivGifEncoder(downloader = PixivHelperDownloader) {

    public override val dir: File get() = ugoiraImagesFolder

    override suspend fun download(url: Url, filename: String) = dir.resolve(filename).apply {
        if (exists().not()) {
            writeBytes(downloader.download(url))
            logger.info { "$filename 下载完成" }
        }
    }

    override val quantizer: ColorQuantizer by lazy { instance(PixivGifConfig.quantizer) }

    override val ditherer: Ditherer by lazy { instance(PixivGifConfig.ditherer) }

    override val disposalMethod: DisposalMethod by lazy { PixivGifConfig.disposal }

    // 考虑到GIF编码需要较高性能
    private val single = Mutex()

    suspend fun build(illust: IllustInfo, metadata: UgoiraMetadata, flush: Boolean): File {
        download(metadata.original, metadata.original.filename)
        val gif = dir.resolve("${illust.pid}.gif")
        return if (flush || gif.exists().not()) {
            single.withLock {
                with(illust) {
                    logger.info {
                        "动图(${pid})<${createAt}>[${user.id}][${title}][${metadata.frames.size}]{${totalBookmarks}}开始编码"
                    }
                }
                encode(illust, metadata)
            }
        } else {
            gif
        }
    }
}