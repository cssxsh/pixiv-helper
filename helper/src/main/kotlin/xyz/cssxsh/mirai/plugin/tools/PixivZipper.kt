package xyz.cssxsh.mirai.plugin.tools

import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.pixiv.model.*
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

    private fun ArtWorkInfo.toSignText() =
        "(${pid})[${getFullWidthTitle()}]{${pageCount}}"

    private fun timestamp() =
        OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

    private fun ArtWorkInfo.getFullWidthTitle() = title.replace(FULLWIDTH_REPLACE_REGEX) {
        FULLWIDTH_REPLACE_CHARS.getOrDefault(it.value, "")
    }

    private fun getZipFile(type: String) =
        PixivHelperSettings.backupFolder.resolve("${type.toUpperCase()}(${timestamp()}).zip").apply { createNewFile() }

    fun compressArtWorks(list: List<ArtWorkInfo>, type: String): File = getZipFile(type).also { zip ->
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
            }
            stream.flush()
        }
        logger.info { "[${zip.name}]压缩完毕！" }
    }

    fun backupData(list: List<Pair<File, String>> = listOf(
        PixivHelperPlugin.dataFolder to "data",
        PixivHelperPlugin.configFolder to "config"
    )): List<File> = list.map { (source, type) ->
        getZipFile(type).also { zip ->
            logger.verbose { "将备份数据目录${source.absolutePath}到${zip.absolutePath}" }
            ZipOutputStream(zip.outputStream().buffered(BUFFER_SIZE)).use { stream ->
                stream.setLevel(Deflater.BEST_COMPRESSION)
                if (source.isDirectory) {
                    source.listFiles { file -> file.isFile }?.forEach { file ->
                        stream.putNextEntry(ZipEntry(file.name).apply {
                            lastModifiedTime = FileTime.fromMillis(file.lastModified())
                        })
                        stream.write(file.readBytes())
                        logger.verbose { "[${file.name}]已写入[${zip.name}]" }
                    }
                } else if (source.isFile) {
                    stream.putNextEntry(ZipEntry(source.name).apply {
                        lastModifiedTime = FileTime.fromMillis(source.lastModified())
                    })
                    stream.write(source.readBytes())
                }
                stream.flush()
            }
            logger.info { "[${zip.name}]压缩完毕！" }
        }
    }
}