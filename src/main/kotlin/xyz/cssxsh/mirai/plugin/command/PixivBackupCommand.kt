package xyz.cssxsh.mirai.plugin.command

import io.github.gnuf0rce.mirai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.*
import org.hibernate.*
import xyz.cssxsh.baidu.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import java.io.*

object PixivBackupCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "backup",
    description = "PIXIV备份指令"
), PixivHelperCommand {

    private val compress = Mutex()

    private fun CommandSender.compress(block: PixivZipper.() -> List<File>) = PixivHelperPlugin.launch(Dispatchers.IO) {
        for (file in compress.withLock { PixivZipper.block() }) {
            if (BackupUpload) {
                sendMessage("${file.name} ${bytes(file.length())} 压缩完毕，开始上传到百度云")
                val message = try {
                    val code = RapidUploadInfo.calculate(file).format()
                    NetDisk.uploadFile(file)
                    logger.info { "[${file.name}]上传成功，百度云标准码: $code " }
                    "[${file.name}]上传成功，百度云标准码: $code"
                } catch (exception: NoClassDefFoundError) {
                    logger.warning { "相关类加载失败，请安装 https://github.com/gnuf0rce/Netdisk-FileSync-Plugin $exception" }
                    "相关类加载失败，请安装 https://github.com/gnuf0rce/Netdisk-FileSync-Plugin $exception"
                } catch (cause: Throwable) {
                    logger.warning({ "[${file.name}]上传失败" }, cause)
                    "[${file.name}]上传失败, ${cause.message}"
                }
                sendMessage(message)
            } else {
                sendMessage("${file.name} ${bytes(file.length())} 压缩完毕，开始发送文件")
                try {
                    file.toExternalResource()
                        .use { (subject as FileSupported).files.uploadNewFile(filepath = file.name, content = it) }
                } catch (cause: Throwable) {
                    sendMessage("[${file.name}]上传失败: ${cause.message}")
                }
            }
        }
    }

    @SubCommand
    @Description("备份指定用户的作品")
    fun CommandSender.user(uid: Long) = compress {
        listOf(artworks(list = ArtWorkInfo.user(uid), basename = "USER[${uid}]"))
    }

    @SubCommand
    @Description("备份已设定别名用户的作品")
    fun CommandSender.alias() = compress {
        AliasSetting.all().mapTo(HashSet()) { it.uid }.map { uid ->
            artworks(list = ArtWorkInfo.user(uid), basename = "USER[${uid}]")
        }
    }

    @SubCommand
    @Description("备份指定标签的作品")
    fun CommandSender.tag(tag: String, marks: Long = 0, fuzzy: Boolean = false) = compress {
        val list = ArtWorkInfo.tag(word = tag, marks = marks, fuzzy = fuzzy, limit = Int.MAX_VALUE, age = AgeLimit.R18G)
        listOf(artworks(list = list, basename = "TAG[${tag}]"))
    }

    @SubCommand
    @Description("备份插件数据")
    fun CommandSender.data() = compress {
        files(list = backups())
    }

    @SubCommand
    @Description("列出备份目录")
    suspend fun CommandSender.list() {
        sendMessage(PixivZipper.list().joinToString("\n") { file ->
            "${file.name} ${bytes(file.length())}"
        })
    }

    @SubCommand
    @Description("获取备份文件，发送文件消息")
    suspend fun UserCommandSender.get(filename: String) {
        try {
            val file = requireNotNull(PixivZipper.find(name = filename)) { "文件不存在" }
            file.toExternalResource()
                .use { (subject as FileSupported).files.uploadNewFile(filepath = file.name, content = it) }
        } catch (cause: Throwable) {
            sendMessage("上传失败: ${cause.message}")
        }
    }

    @SubCommand
    @Description("上传插件数据到百度云")
    suspend fun CommandSender.upload(filename: String) {
        val file = requireNotNull(PixivZipper.find(name = filename)) { "文件不存在" }
        val message = try {
            val code = RapidUploadInfo.calculate(file).format()
            val info = NetDisk.uploadFile(file = file)
            logger.info { "$code 上传成功: $info" }
            "$code 上传成功: $info"
        } catch (exception: NoClassDefFoundError) {
            logger.warning { "相关类加载失败，请安装 https://github.com/gnuf0rce/Netdisk-FileSync-Plugin $exception" }
            "相关类加载失败，请安装 https://github.com/gnuf0rce/Netdisk-FileSync-Plugin $exception"
        } catch (cause: Throwable) {
            logger.warning({ "[${name}]上传失败" }, cause)
            "[${name}]上传失败, ${cause.message}"
        }

        sendMessage(message)
    }

    @SubCommand
    @Description("从 sqlite 备份中导入数据")
    suspend fun CommandSender.reload(path: String, mode: ReplicationMode, chunk: Int = 8196) {
        reload(path, mode, chunk)
        sendMessage("导入完成")
    }
}