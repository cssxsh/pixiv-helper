package xyz.cssxsh.mirai.plugin.tools

import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import java.io.*
import java.nio.file.attribute.*
import java.time.*
import java.util.zip.*
import kotlin.collections.*

object PixivZipper {

    /**
     * 64MB
     */
    private const val BUFFER_SIZE = 64 * 1024 * 1024

    private val FULLWIDTH = mapOf(
        '\\' to '＼',
        '/' to '／',
        ':' to '：',
        '*' to '＊',
        '?' to '？',
        '＂' to '＂',
        '<' to '＜',
        '>' to '＞',
        '|' to '｜'
    )

    private fun ArtWorkInfo.getFullWidthTitle() = title.fold("") { acc, char -> acc + (FULLWIDTH[char] ?: char) }

    private fun ArtWorkInfo.toSignText() = "(${pid})[${title}]{${pages}}"

    private fun getZipFile(basename: String) = PixivHelperSettings.backupFolder.resolve("${basename}.zip").apply {
        renameTo(parentFile.resolve("${basename}.old").apply { delete() })
        createNewFile()
    }

    fun list() = PixivHelperSettings.backupFolder.listFiles { file -> file.isFile && file.extension == "zip" }.orEmpty()

    fun find(name: String) = list().firstOrNull { file -> file.name.startsWith(name) }

    fun compressArtWorks(list: List<ArtWorkInfo>, basename: String): File = getZipFile(basename).also { zip ->
        logger.verbose { "共${list.size}个作品将写入文件${zip.absolutePath}" }
        ZipOutputStream(zip.outputStream().buffered(BUFFER_SIZE)).use { stream ->
            stream.setLevel(Deflater.BEST_COMPRESSION)
            for (info in list) {
                for (file in imagesFolder(info.pid).listFiles().orEmpty()) {
                    stream.putNextEntry(ZipEntry("[${info.pid}](${info.getFullWidthTitle()})/${file.name}").apply {
                        creationTime = FileTime.from(Instant.ofEpochSecond(info.created))
                        lastModifiedTime = FileTime.from(Instant.ofEpochSecond(info.created))
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

    private fun ZipOutputStream.addFile(file: File, zip: File, root: String = file.parent) {
        putNextEntry(ZipEntry(file.path.removePrefix(root).removePrefix(File.separator)).apply {
            time = file.lastModified()
        })
        write(file.readBytes())
        flush()
        logger.verbose { "[${file.name}]已写入[${zip.name}]" }
        System.gc()
    }

    private fun ZipOutputStream.addDir(dir: File, zip: File, root: String = dir.parent) {
        dir.listFiles()?.forEach {
            if (it.isFile) {
                addFile(file = it, zip = zip, root = root)
            } else {
                addDir(dir = it, zip = zip, root = root)
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