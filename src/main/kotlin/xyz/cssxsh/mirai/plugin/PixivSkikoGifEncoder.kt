package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import net.mamoe.mirai.utils.*
import org.jetbrains.skia.*
import xyz.cssxsh.gif.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.tool.*
import java.io.*
import java.util.zip.*

object PixivSkikoGifEncoder : PixivGifEncoder(downloader = PixivHelperDownloader) {

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

    override suspend fun encode(illust: IllustInfo, metadata: UgoiraMetadata, loop: Int): File {
        val file = metadata.download()
        val gif = cache.resolve("${illust.pid}.gif")
        val temp = cache.resolve("${illust.pid}.temp")
        withContext(Dispatchers.IO) {
            val zip = ZipFile(file)
            val first =
                Image.makeFromEncoded(zip.getInputStream(zip.getEntry(metadata.frames.first().file)).readBytes())
            val encoder = Encoder(temp, first.width, first.height)
            encoder.repeat = if (loop > 0) loop else -1

            for (frame in metadata.frames) {
                val image = Image.makeFromEncoded(zip.getInputStream(zip.getEntry(frame.file)).readBytes())
                encoder.writeImage(
                    image = image,
                    mills = frame.delay.toInt(),
                    disposal = AnimationDisposalMode.RESTORE_PREVIOUS
                )
                image.close()
            }

            zip.close()
            encoder.close()
        }
        temp.renameTo(gif)
        return gif
    }

    private val single = Mutex()

    suspend fun build(illust: IllustInfo, metadata: UgoiraMetadata, flush: Boolean): File {
        metadata.download()
        val gif = cache.resolve("${illust.pid}.gif")
        Library.staticLoad()
        return if (flush || gif.exists().not()) {
            gif.delete()
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