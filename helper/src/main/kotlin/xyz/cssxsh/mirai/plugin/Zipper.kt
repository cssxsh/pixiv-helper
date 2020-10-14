package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.cssxsh.mirai.plugin.command.PixivCacheCommand
import xyz.cssxsh.mirai.plugin.data.BaseInfo
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import java.io.BufferedOutputStream
import java.io.File
import java.nio.file.attribute.FileTime
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Zipper: PixivHelperLogger {
    private fun BaseInfo.getFullWidthTitle() = title.replace("""[\\/:*?"<>|]""".toRegex()) {
        mapOf("\\" to "＼", "/" to "／", ":" to "：", "*" to "＊", "?" to "？", "\"" to "＂", "<" to "＜", ">" to "＞", "|" to "｜").run {
            getOrDefault(it.value, "")
        }
    }

    fun CoroutineScope.compress(list: List<BaseInfo>, path: String) = launch(Dispatchers.IO) {
        logger.verbose("共${list.size} 个作品将写入文件${path}")
        ZipOutputStream(BufferedOutputStream(File(path).apply {
            createNewFile()
        }.outputStream(), 64 * 1024 * 1024)).use { zipOutputStream ->
            zipOutputStream.setLevel(Deflater.BEST_COMPRESSION)
            list.forEach { info ->
                PixivHelperSettings.imagesFolder(info.pid).listFiles()?.forEach { file ->
                    zipOutputStream.putNextEntry(ZipEntry("[${info.pid}](${info.getFullWidthTitle()})/${file.name}").apply {
                        creationTime = FileTime.fromMillis(info.createDate.utc.unixMillisLong)
                        lastModifiedTime = FileTime.fromMillis(info.createDate.utc.unixMillisLong)
                    })
                    zipOutputStream.write(file.readBytes())
                }
            }
        }
        logger.verbose("${path}压缩完毕！")
    }
}