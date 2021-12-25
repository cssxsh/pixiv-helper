package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import kotlinx.coroutines.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScope
import net.mamoe.mirai.utils.*
import okio.ByteString.Companion.decodeHex
import xyz.cssxsh.mirai.plugin.model.*
import java.io.*

class PixivImageResource(val info: FileInfo) : AbstractExternalResource(displayName = "${info.pid}_p${info.index}") {

    @OptIn(ConsoleExperimentalApi::class)
    companion object : CoroutineScope by PixivHelperPlugin.childScope("PixivImageResource")

    override val md5: ByteArray get() = info.md5.decodeHex().toByteArray()

    override val size: Long get() = info.size.toLong()

    private val url = Url(info.url)

    override val origin = images(pid = info.pid).resolve(url.filename)

    override fun inputStream0(): InputStream {
        return if (origin.exists()) {
            origin.inputStream()
        } else {
            val bytes = runBlocking(coroutineContext) { PixivHelperDownloader.download(url) }
            origin.writeBytes(bytes)
            bytes.inputStream()
        }
    }
}