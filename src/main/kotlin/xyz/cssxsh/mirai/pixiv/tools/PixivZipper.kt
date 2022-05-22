package xyz.cssxsh.mirai.pixiv.tools

import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.model.*
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

    private fun zip(basename: String) = BackupFolder.resolve("${basename}.zip").apply {
        renameTo(parentFile.resolve("${basename}.old").apply { delete() })
        createNewFile()
    }

    fun list() = BackupFolder.listFiles { file -> file.isFile && file.extension == "zip" }.orEmpty()

    fun find(name: String) = list().firstOrNull { file -> file.name.startsWith(name) }

    fun artworks(list: List<ArtWorkInfo>, basename: String): File = zip(basename).also { zip ->
        logger.verbose { "共${list.size}个作品将写入文件${zip.absolutePath}" }
        ZipOutputStream(zip.outputStream().buffered(BUFFER_SIZE)).use { stream ->
            stream.setLevel(Deflater.BEST_COMPRESSION)
            for (info in list) {
                for (file in images(info.pid).listFiles().orEmpty()) {
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

    private fun ZipOutputStream.addDirectory(directory: File, zip: File, root: String = directory.parent) {
        for (item in directory.listFiles().orEmpty()) {
            if (item.isFile) {
                addFile(file = item, zip = zip, root = root)
            } else {
                addDirectory(directory = item, zip = zip, root = root)
            }
        }
    }

    fun files(list: Map<String, File>): List<File> = list.map { (basename, source) ->
        zip(basename).also { zip ->
            logger.verbose { "将备份数据目录${source.absolutePath}到${zip.absolutePath}" }
            ZipOutputStream(zip.outputStream().buffered(BUFFER_SIZE)).use { stream ->
                stream.setLevel(Deflater.BEST_COMPRESSION)
                if (source.isDirectory) {
                    stream.addDirectory(directory = source, zip = zip)
                } else if (source.isFile) {
                    stream.addFile(file = source, zip = zip)
                }
            }
            logger.info { "[${zip.name}]压缩完毕！" }
        }
    }
}