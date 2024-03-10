package xyz.cssxsh.mirai.pixiv

import io.ktor.http.*
import net.mamoe.mirai.utils.*
import org.jcodec.api.awt.AWTSequenceEncoder
import org.jcodec.common.io.NIOUtils
import org.jcodec.common.model.Rational
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.tool.*
import java.io.*
import java.util.zip.ZipFile
import javax.imageio.ImageIO

public object PixivHelperMP4Encoder : PixivGifEncoder(downloader = PixivHelperDownloader) {

    public override val cache: File get() = UgoiraImagesFolder

    override suspend fun download(url: Url, filename: String): File {
        val pid = filename.substringBefore('_').toLong()
        return images(pid).resolve(filename).apply {
            if (exists().not()) {
                if (PixivHelperGifEncoder.cache.resolve(filename).exists()) {
                    PixivHelperGifEncoder.cache.resolve(filename).renameTo(this)
                } else {
                    writeBytes(downloader.download(url))
                    logger.info { "$filename 下载完成" }
                }
            }
        }
    }

    override suspend fun encode(illust: IllustInfo, metadata: UgoiraMetadata, loop: Int): File {
        val pack = metadata.download()
        val mp4 = PixivHelperGifEncoder.cache.resolve("${illust.pid}.mp4")
        if (mp4.exists().not()) {
            with(illust) {
                logger.info {
                    "动画(${pid})<${createAt}>[${user.id}][${title}][${metadata.frames.size}]{${totalBookmarks}}开始编码"
                }
            }
            NIOUtils.writableChannel(mp4).use { out ->
                val encoder = AWTSequenceEncoder(out, Rational.R(100, 1))
                ZipFile(pack).use { zip ->
                    for (frame in metadata.frames) {
                        val entry = zip.getEntry(frame.file) ?: throw FileNotFoundException(frame.file)
                        val image = ImageIO.read(zip.getInputStream(entry))
                        repeat((frame.delay / 10).toInt()) {
                            encoder.encodeImage(image)
                        }
                    }
                }
                encoder.finish()
            }
        }
        return mp4
    }
}