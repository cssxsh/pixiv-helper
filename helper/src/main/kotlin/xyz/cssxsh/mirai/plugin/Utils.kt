package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.UserCommandSender
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.uploadAsImage
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.pixiv.data.app.IllustInfo
import xyz.cssxsh.pixiv.tool.downloadImage
import java.io.File

/**
 * 获取对应subject的助手
 */
fun UserCommandSender.getHelper() = PixivHelper(subject)

/**
 * 获取对应subject的助手
 */
fun <T : MessageEvent> CommandSenderOnMessage<T>.getHelper() = PixivHelperManager[fromEvent.subject]

fun Long.positiveLongCheck() = also { require(it > 0) { "应该为正整数" } }

fun IllustInfo.getMessage(): Message = buildString {
    appendLine("作者: ${user.name} ")
    appendLine("UID: ${user.id} ")
    appendLine("健全等级: $sanityLevel ")
    appendLine("创作于: ${createDate.format("yyyy-MM-dd'T'HH:mm:ssXXX")} ")
    appendLine("共: $pageCount 张图片 ")
    appendLine("Pixiv_Net: https://www.pixiv.net/artworks/${pid} ")
    appendLine("标签：${tags.map { it.name }}")
    getPixivCatUrls(pid, pageCount).forEach { appendLine(it) }
}.let { PlainText(it) }

suspend fun PixivHelper.buildMessage(
    illust: IllustInfo,
    type: String = "origin"
): List<Message> = buildList {
    add(illust.getMessage())
    if (!illust.isR18()) {
        addAll(getImages(illust, type).map {
            it.uploadAsImage(contact)
        })
    } else {
        add(PlainText("R18禁止！"))
    }
}

fun IllustInfo.getPixivCatUrls() = getPixivCatUrls(pid, pageCount)

fun getPixivCatUrls(pid: Long, count: Int): List<String> = if (count > 1) {
    (1..count).map { "https://pixiv.cat/${pid}-${it}.jpg" }
} else {
    listOf("https://pixiv.cat/${pid}.jpg")
}

fun IllustInfo.isR18(): Boolean = tags.any { Regex("""R-?18""") in it.name }

fun IllustInfo.save(cover: Boolean = true) = (pid !in PixivCacheData.illust || cover).also {
    if (it) PixivCacheData.add(this)
}

suspend fun PixivHelper.getImages(
    illust: IllustInfo,
    type: String = "origin"
) : List<File> = PixivHelperPlugin.imagesFolder(illust.pid).let { dir ->
    if (dir.exists()) {
        illust.getImageUrls().flatMap { fileUrls ->
            fileUrls.filter { type in it.key }.values
        }.mapIndexed { index, _ ->
            val name = "${illust.pid}-${type}-${index}.jpg"
            File(dir, name)
        }
    } else {
        dir.mkdir()
        downloadImage<ByteArray>(illust, { name, _ -> type in name }).mapIndexed { index, result ->
            val name = "${illust.pid}-${type}-${index}.jpg"
            File(dir, name).apply {
                writeBytes(result.getOrThrow())
                PixivHelperPlugin.logger.verbose("文件${name}已保存")
            }
        }
    }
}.also {
    if (type == "origin") illust.save()
}




