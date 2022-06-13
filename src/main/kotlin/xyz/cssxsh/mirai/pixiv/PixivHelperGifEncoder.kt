package xyz.cssxsh.mirai.pixiv

import com.squareup.gifencoder.*
import io.ktor.http.*
import kotlinx.coroutines.sync.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.tool.*
import java.io.*

public object PixivHelperGifEncoder : PixivGifEncoder(downloader = PixivHelperDownloader) {

    public override val cache: File get() = UgoiraImagesFolder

    override suspend fun download(url: Url, filename: String): File {
        val pid = filename.substringBefore('_').toLong()
        return images(pid).resolve(filename).apply {
            if (exists().not()) {
                if (cache.resolve(filename).exists()) {
                    cache.resolve(filename).renameTo(this)
                } else {
                    writeBytes(downloader.download(url))
                    logger.info { "$filename 下载完成" }
                }
            }
        }
    }

    override val quantizer: ColorQuantizer by lazy { instance(PixivGifConfig.quantizer) }

    override val ditherer: Ditherer by lazy { instance(PixivGifConfig.ditherer) }

    override val disposalMethod: DisposalMethod by lazy { PixivGifConfig.disposal }

    // 考虑到GIF编码需要较高性能
    private val single = Mutex()

    public suspend fun build(illust: IllustInfo, metadata: UgoiraMetadata, flush: Boolean): File {
        metadata.download()
        val gif = cache.resolve("${illust.pid}.gif")
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