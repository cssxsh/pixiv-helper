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

    public override val dir: File by lazy {
        PixivHelperSettings.tempFolder.resolve("gif").apply { mkdirs() }
    }

    override suspend fun download(url: Url, filename: String) = dir.resolve(filename).apply {
        if (exists().not()) {
            writeBytes(downloader.download(url))
            logger.info { "$filename 下载完成" }
        }
    }

    override val quantizer: ColorQuantizer by lazy {
        System.getProperty(OpenCVQuantizer.MAX_COUNT, "${PixivGifConfig.maxCount}")
        instance(PixivGifConfig.quantizer)
    }

    override val ditherer: Ditherer by lazy {
        instance(PixivGifConfig.ditherer)
    }

    override val disposalMethod: DisposalMethod by lazy {
        PixivGifConfig.disposal
    }

    private val single = Mutex()

    suspend fun build(illust: IllustInfo, metadata: UgoiraMetadata, flush: Boolean): File = single.withLock {
        download(metadata.original, metadata.original.filename)
        dir.resolve("${illust.pid}.gif")
            .takeUnless { flush || it.exists().not() } ?: encode(illust, metadata)
    }
}