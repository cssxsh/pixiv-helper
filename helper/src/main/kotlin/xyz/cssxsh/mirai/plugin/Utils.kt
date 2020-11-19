package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.network.sockets.*
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.uploadAsImage
import okhttp3.internal.http2.StreamResetException
import xyz.cssxsh.mirai.plugin.data.BaseInfo
import xyz.cssxsh.mirai.plugin.data.BaseInfo.Companion.toBaseInfo
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.pixiv.WorkContentType
import xyz.cssxsh.pixiv.api.app.illustDetail
import xyz.cssxsh.pixiv.data.app.IllustInfo
import xyz.cssxsh.pixiv.tool.downloadImageUrls
import java.io.EOFException
import java.io.File
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * 获取对应subject的助手
 */
fun <T : MessageEvent> CommandSenderOnMessage<T>.getHelper() = PixivHelperManager[fromEvent.subject]

/**
 * 运行助手
 */
suspend fun <T : MessageEvent> CommandSenderOnMessage<T>.runHelper(block: PixivHelper.(MessageChain) -> Any) {
    getHelper().runCatching {
        block(message)
    }.onSuccess { result ->
        when (result) {
            is MessageChain -> quoteReply(result)
            is Message -> quoteReply(result)
            is String -> quoteReply(result)
            is Iterable<*> -> result.forEach {
                when (it) {
                    is MessageChain -> quoteReply(it)
                    is Message -> quoteReply(it)
                    is String -> quoteReply(it)
                    else -> quoteReply(it.toString())
                }
            }
            else -> quoteReply(result.toString())
        }
    }.onFailure {
        quoteReply("执行失败， ${it.message}")
    }
}

fun String.getFilename() = substring(lastIndexOfAny(listOf("/", "\\")))

fun IllustInfo.getMessage(): Message = toBaseInfo().getMessage()

fun BaseInfo.getMessage(): Message = buildString {
    appendLine("作者: $uname ")
    appendLine("UID: $uid ")
    appendLine("收藏数: $totalBookmarks ")
    appendLine("健全等级: $sanityLevel ")
    appendLine("创作于: ${getCreateDateText()} ")
    appendLine("共: $pageCount 张图片 ")
    appendLine("Pixiv_Net: https://www.pixiv.net/artworks/${pid} ")
    appendLine("标签：${tags.map { it.name }}")
}.let { PlainText(it) }

suspend fun PixivHelper.buildMessage(
    illust: IllustInfo,
    save: Boolean = true
): List<Message> = buildList {
    illust.writeToCache()
    if (simpleInfo) {
        add(PlainText("作品ID: ${illust.pid}, 收藏数: ${illust.totalBookmarks}, 健全等级: ${illust.sanityLevel} "))
    } else {
        add(illust.getMessage())
        add(PlainText(buildString {
            appendLine("原图连接: ")
            illust.getPixivCatUrls().forEach {
                appendLine(it)
            }
        }))
    }
    if (!illust.isR18()) {
        getImages(illust, save).map {
            add(it.uploadAsImage(contact))
        }
    } else {
        add(PlainText("R18禁止！"))
    }
}

suspend fun PixivHelper.buildMessage(
    info: BaseInfo
): List<Message> = buildList {
    if (simpleInfo) {
        add(PlainText("作品ID: ${info.pid}, 收藏数: ${info.totalBookmarks}, 健全等级: ${info.sanityLevel} "))
    } else {
        add(info.getMessage())
    }
    if (!info.isR18()) {
        addAll(getImages(info).map {
            it.uploadAsImage(contact)
        })
    } else {
        add(PlainText("R18禁止！"))
    }
}

fun IllustInfo.getPixivCatUrls(): List<String> = buildList {
    if (pageCount > 1) {
        (1..pageCount).forEach {
            add("https://pixiv.cat/${pid}-${it}.jpg")
        }
    } else {
        add("https://pixiv.cat/${pid}.jpg")
    }
}

fun IllustInfo.isR18(): Boolean =
    tags.any { """R-?18""".toRegex() in it.name || "精液" in it.name || "中出" in it.name }

fun BaseInfo.isR18(): Boolean =
    tags.any { """R-?18""".toRegex() in it.name || "精液" in it.name || "中出" in it.name }

fun IllustInfo.isEro(): Boolean =
    totalBookmarks ?: 0 >= PixivHelperSettings.totalBookmarks && pageCount < 4 && type == WorkContentType.ILLUST

fun BaseInfo.isEro(): Boolean =
    totalBookmarks >= PixivHelperSettings.totalBookmarks && pageCount < 4 && type == WorkContentType.ILLUST

fun IllustInfo.save() = PixivCacheData.put(this)

fun IllustInfo.writeTo(
    file: File
) = file.writeText(
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        allowStructuredMapKeys = true
    }.encodeToString(IllustInfo.serializer(), this)
)

fun IllustInfo.writeToCache() = writeTo(File(PixivHelperSettings.imagesFolder(pid), "${pid}.json"))

fun Collection<IllustInfo>.writeToCache() = forEach { illust ->
    illust.writeToCache()
}

fun File.readIllustInfo(): IllustInfo = readText().let {
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        allowStructuredMapKeys = true
    }.decodeFromString(IllustInfo.serializer(), it)
}

suspend fun PixivHelper.getIllustInfo(
    pid: Long,
    flush: Boolean = false,
    block: suspend PixivHelper.(Long) -> IllustInfo = { illustDetail(it).illust }
): IllustInfo = PixivHelperSettings.imagesFolder(pid).let { dir ->
    File(dir, "${pid}.json").let { file ->
        if (!flush && file.canRead()) {
            file.readIllustInfo()
        } else {
            block(pid).apply {
                writeTo(file)
            }
        }
    }
}

suspend fun PixivHelper.getImages(
    info: BaseInfo
): List<File> = getImages(
    pid = info.pid,
    urls = info.originUrl
)

suspend fun PixivHelper.getImages(
    illust: IllustInfo,
    save: Boolean = true
): List<File> = getImages(pid = illust.pid, urls = illust.getOriginUrl()).apply {
    if (save) {
        illust.save()
    }
}

suspend fun PixivHelper.downloadImageUrls(urls: List<String>, dir: File): List<Result<File>> = downloadImageUrls(
    urls = urls,
    ignore = { url, throwable ->
        when (throwable) {
            is SSLHandshakeException,
            is SSLException,
            is EOFException,
            is StreamResetException,
            is SocketTimeoutException,
            is ConnectTimeoutException,
            is HttpRequestTimeoutException -> {
                logger.warning { "${url}下载错误, 已忽略: ${throwable.message}" }
                true
            }
            else -> false
        }
    },
    block = { _, url, result ->
        runCatching {
            File(dir, url.getFilename()).apply {
                writeBytes(result.getOrThrow())
            }
        }
    }
)

suspend fun PixivHelper.getImages(
    pid: Long,
    urls: List<String>
): List<File> = PixivHelperSettings.imagesFolder(pid).let { dir ->
    if (File(dir, urls.first().getFilename()).canRead()) {
        urls.map { url ->
            File(dir, url.getFilename())
        }
    } else {
        downloadImageUrls(urls = urls, dir = dir).map {
            it.getOrThrow()
        }
    }
}




