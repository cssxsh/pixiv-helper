package xyz.cssxsh.mirai.plugin

import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.uploadAsImage
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.pixiv.api.app.illustDetail
import xyz.cssxsh.pixiv.data.app.IllustInfo
import xyz.cssxsh.pixiv.tool.downloadImage
import java.io.File

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

fun Long.positiveLongCheck() = also { require(it > 0) { "应该为正整数" } }

fun IllustInfo.getMessage(): Message = buildString {
    appendLine("作者: ${user.name} ")
    appendLine("UID: ${user.id} ")
    appendLine("健全等级: $sanityLevel ")
    appendLine("创作于: ${createDate.format("yyyy-MM-dd'T'HH:mm:ssXXX")} ")
    appendLine("共: $pageCount 张图片 ")
    appendLine("Pixiv_Net: https://www.pixiv.net/artworks/${pid} ")
    appendLine("标签：${tags.map { it.name }}")
    getPixivCatUrls().forEach { appendLine(it) }
}.let {
    PlainText(it)
}

suspend fun PixivHelper.buildMessage(
    illust: IllustInfo,
    save: Boolean = true
): List<Message> = buildList {
    if (simpleInfo) {
        add(PlainText("作品ID: ${illust.pid}"))
    } else {
        add(illust.getMessage())
    }
    if (!illust.isR18()) {
        addAll(getImages(getImageInfo(illust.pid) {
            illust
        }, save).map {
            it.uploadAsImage(contact)
        })
    } else {
        add(PlainText("R18禁止！"))
    }
}

fun IllustInfo.getPixivCatUrls(): List<String> = if (pageCount > 1) {
    (1..pageCount).map { "https://pixiv.cat/${pid}-${it}.jpg" }
} else {
    listOf("https://pixiv.cat/${pid}.jpg")
}

fun IllustInfo.isR18(): Boolean = tags.any { """R-?18""".toRegex() in it.name }

fun IllustInfo.save() = (pid !in PixivCacheData).also {
    if (it) PixivCacheData.add(this)
}

fun IllustInfo.writeTo(file: File) = file.writeText(
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        allowStructuredMapKeys = true
    }.encodeToString(IllustInfo.serializer(), this)
)

fun File.readIllustInfo(): IllustInfo = readText().let {
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        allowStructuredMapKeys = true
    }.decodeFromString(IllustInfo.serializer(), it)
}

suspend fun PixivHelper.getImageInfo(
    pid: Long,
    block: suspend PixivHelper.(Long) -> IllustInfo = { illustDetail(it).illust }
): IllustInfo = PixivHelperSettings.imagesFolder(pid).let { dir ->
    File(dir, "${pid}.json").let { file ->
        if (file.canRead()) {
            file.readIllustInfo()
        } else {
            block(pid).also {
                it.writeTo(file)
            }
        }
    }
}

suspend fun PixivHelper.getImages(
    illust: IllustInfo,
    save: Boolean = true
): List<File> = PixivHelperSettings.imagesFolder(illust.pid).let { dir ->
    if (File(dir, "${illust.pid}-origin-0.jpg").canRead()) {
        illust.getOriginUrl().mapIndexed { index, _ ->
            File(dir, "${illust.pid}-origin-${index}.jpg")
        }
    } else {
        downloadImage<ByteArray>(illust).mapIndexed { index, result ->
            File(dir, "${illust.pid}-origin-${index}.jpg").apply {
                writeBytes(result.getOrThrow())
            }
        }
    }
}.apply {
    if (save) illust.save()
}




