package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.UserCommandSender
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.toExternalImage
import net.mamoe.mirai.utils.upload
import xyz.cssxsh.mirai.plugin.PixivHelper
import xyz.cssxsh.mirai.plugin.PixivHelperManager
import xyz.cssxsh.pixiv.data.app.IllustInfo
import xyz.cssxsh.pixiv.download.downloadImage
import java.io.InputStream


/**
 * 获取对应subject的助手
 */
fun UserCommandSender.getHelper() = PixivHelper(subject)

/**
 * 获取对应subject的助手
 */
fun <T : MessageEvent> CommandSenderOnMessage<T>.getHelper() = PixivHelperManager[fromEvent.subject]

fun IllustInfo.getMessage(): Message = buildString {
    appendLine("作者: ${user.name} ")
    appendLine("PID: ${user.id} ")
    appendLine("创作于: ${createDate.format("yyyy-MM-dd'T'HH:mm:ssXXX")} ")
    appendLine("共: $pageCount 张图片 ")
    appendLine("Pixiv_Net: https://www.pixiv.net/artworks/${pid} ")
    getPixivCatUrls(pid, pageCount).forEach { appendLine(it) }
}.let { PlainText(it) }

suspend fun PixivHelper.buildMessage(
    illust: IllustInfo,
    type: String = "origin"
): List<Message> = buildList {
    add(illust.getMessage())
    if (!illust.isR18()) {
        downloadImage<InputStream>(illust, { key -> type in key }).mapIndexed { index, result ->
            result.onSuccess {
                add(it.runCatching {
                    toExternalImage().upload(contact)
                }.getOrDefault(PlainText("$index 图上传失败！")))
            }.onFailure {
                add(PlainText("$index 图下载失败！"))
            }
        }
    } else {
        add(PlainText("R18禁止！"))
    }
}

fun getPixivCatUrls(pid: Long, count: Int): List<String> = if (count > 1) {
    (1..count).map { "https://pixiv.cat/${pid}-${it}.jpg" }
} else {
    listOf("https://pixiv.cat/${pid}.jpg")
}

fun IllustInfo.isR18(): Boolean = tags.any { "R-18" in it.name }




