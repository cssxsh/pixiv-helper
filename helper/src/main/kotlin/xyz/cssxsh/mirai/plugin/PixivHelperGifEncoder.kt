package xyz.cssxsh.mirai.plugin

import com.squareup.gifencoder.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.apps.IllustInfo
import xyz.cssxsh.pixiv.apps.UgoiraMetadata
import xyz.cssxsh.pixiv.tool.*
import java.io.*

object PixivHelperGifEncoder : PixivGifEncoder(downloader = PixivHelperDownloader) {

    override val dir: File by lazy {
        PixivHelperSettings.tempFolder.resolve("gif").apply { mkdirs() }
    }

    override val quantizer: ColorQuantizer by lazy { instance(PixivGifConfig.quantizer) }

    override val ditherer: Ditherer by lazy { instance(PixivGifConfig.ditherer) }

    override val disposalMethod: DisposalMethod by lazy { PixivGifConfig.disposal }

    suspend fun build(illust: IllustInfo, metadata: UgoiraMetadata, flush: Boolean): File {
        return dir.resolve("${illust.pid}.gif").takeUnless {
            flush || it.exists().not()
        } ?: encode(illust.pid, metadata, illust.width, illust.height)
    }
}