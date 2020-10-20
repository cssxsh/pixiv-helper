package xyz.cssxsh.mirai.plugin

import com.soywiz.klock.DateFormat.Companion.FORMAT_DATE
import com.soywiz.klock.wrapped.WDateTimeTz
import kotlinx.coroutines.*
import xyz.cssxsh.mirai.plugin.data.BaseInfo
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import java.io.BufferedOutputStream
import java.io.File
import java.nio.file.attribute.FileTime
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Zipper: PixivHelperLogger {

    private val charMap = mapOf("\\" to "＼", "/" to "／", ":" to "：", "*" to "＊", "?" to "？", "\"" to "＂", "<" to "＜", ">" to "＞", "|" to "｜")

    private const val BUFFER_SIZE = 64 * 1024 * 1024

    private fun BaseInfo.toInfo() =
        "(${pid})${getFullWidthTitle()}]{${pageCount}}"

    private fun BaseInfo.getFullWidthTitle() = title.replace("""[\\/:*?"<>|]""".toRegex()) {
        charMap.getOrDefault(it.value, "")
    }

    fun compress(list: List<BaseInfo>, filename: String) = PixivHelperPlugin.launch(Dispatchers.IO) {
        logger.verbose("共${list.size}个作品将写入文件${filename}")
        ZipOutputStream(BufferedOutputStream(File(PixivHelperSettings.zipFolder, filename).apply {
            createNewFile()
        }.outputStream(), BUFFER_SIZE)).use { zipOutputStream ->
            zipOutputStream.setLevel(Deflater.BEST_COMPRESSION)
            list.forEach { info ->
                PixivHelperSettings.imagesFolder(info.pid).listFiles()?.forEach { file ->
                    zipOutputStream.putNextEntry(ZipEntry("[${info.pid}](${info.getFullWidthTitle()})/${file.name}").apply {
                        creationTime = FileTime.fromMillis(info.createDate.utc.unixMillisLong)
                        lastModifiedTime = FileTime.fromMillis(info.createDate.utc.unixMillisLong)
                    })
                    zipOutputStream.write(file.readBytes())
                }
                logger.verbose("${info.toInfo()}已写入${filename}")
            }
            zipOutputStream.flush()
        }
        logger.verbose("${filename}压缩完毕！")
    }

    fun backup() = PixivHelperPlugin.launch(Dispatchers.IO) {
        PixivHelperPlugin.dataFolder.run {
            logger.verbose("将备份数据目录${absolutePath}")
            ZipOutputStream(BufferedOutputStream(File("date-${WDateTimeTz.nowLocal().format(FORMAT_DATE)}.zip").apply {
                createNewFile()
            }.outputStream(), BUFFER_SIZE)).use { zipOutputStream ->
                zipOutputStream.setLevel(Deflater.BEST_COMPRESSION)
                listFiles { file -> file.isFile }?.forEach { file ->
                    zipOutputStream.putNextEntry(ZipEntry(file.name))
                    zipOutputStream.write(file.readBytes())
                }
                zipOutputStream.flush()
            }
        }
    }
}