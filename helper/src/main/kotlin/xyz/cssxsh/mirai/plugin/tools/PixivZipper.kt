package xyz.cssxsh.mirai.plugin.tools

import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import java.io.File
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PixivZipper {

    private val FULLWIDTH_REPLACE_CHARS = mapOf(
        "\\" to "＼",
        "/" to "／",
        ":" to "：",
        "*" to "＊",
        "?" to "？",
        "\"" to "＂",
        "<" to "＜",
        ">" to "＞",
        "|" to "｜"
    )

    private val FULLWIDTH_REPLACE_REGEX = """[\\/:*?"<>|]""".toRegex()

    /**
     * 64MB
     */
    private const val BUFFER_SIZE = 64 * 1024 * 1024

    private fun ArtWorkInfo.toSignText() = "(${pid})[${getFullWidthTitle()}]{${pageCount}}"

    private fun timestamp() =
        OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

    private fun ArtWorkInfo.getFullWidthTitle() = title.replace(FULLWIDTH_REPLACE_REGEX) {
        FULLWIDTH_REPLACE_CHARS.getOrDefault(it.value, "#")
    }

    private fun getZipFile(basename: String) =
        PixivHelperSettings.backupFolder.resolve("${basename}(${timestamp()}).zip").apply { createNewFile() }

    fun listZipFiles() =
        PixivHelperSettings.backupFolder.listFiles { file -> file.isFile && file.extension == "zip" }.orEmpty()

    fun compressArtWorks(list: List<ArtWorkInfo>, basename: String): File = getZipFile(basename).also { zip ->
        logger.verbose { "共${list.size}个作品将写入文件${zip.absolutePath}" }
        ZipOutputStream(zip.outputStream().buffered(BUFFER_SIZE)).use { stream ->
            stream.setLevel(Deflater.BEST_COMPRESSION)
            list.forEach { info ->
                PixivHelperSettings.imagesFolder(info.pid).listFiles()?.forEach { file ->
                    stream.putNextEntry(ZipEntry("[${info.pid}](${info.getFullWidthTitle()})/${file.name}").apply {
                        creationTime = FileTime.from(Instant.ofEpochSecond(info.createAt))
                        lastModifiedTime = FileTime.from(Instant.ofEpochSecond(info.createAt))
                    })
                    stream.write(file.readBytes())
                }
                logger.verbose { "${info.toSignText()}已写入[${zip.name}]" }
                System.gc()
            }
            stream.flush()
        }
        logger.info { "[${zip.name}]压缩完毕！" }
    }

    private fun ZipOutputStream.addFile(file: File, path: String = "", zip: File) {
        putNextEntry(ZipEntry("${path}${file.name}").apply {
            time = file.lastModified()
        })
        write(file.readBytes())
        flush()
        logger.verbose { "[${file.name}]已写入[${zip.name}]" }
        System.gc()
    }

    private fun ZipOutputStream.addDir(dir: File, path: String = "", zip: File) {
        dir.listFiles()?.forEach {
            if (it.isFile) {
                addFile(file = it, path = "${path}${dir.name}/", zip = zip)
            } else {
                addDir(dir = it, path = "${path}${dir.name}/", zip = zip)
            }
        }
    }

    fun compressData(list: Map<String, File>): List<File> = list.map { (basename, source) ->
        getZipFile(basename).also { zip ->
            logger.verbose { "将备份数据目录${source.absolutePath}到${zip.absolutePath}" }
            ZipOutputStream(zip.outputStream().buffered(BUFFER_SIZE)).use { stream ->
                stream.setLevel(Deflater.BEST_COMPRESSION)
                if (source.isDirectory) {
                    stream.addDir(dir = source, zip = zip)
                } else if (source.isFile) {
                    stream.addFile(file = source, zip = zip)
                }
            }
            logger.info { "[${zip.name}]压缩完毕！" }
        }
    }
}