package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.RemoteFile.Companion.sendFile
import xyz.cssxsh.baidu.oauth.*
import xyz.cssxsh.baidu.getRapidUploadInfo
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.tools.*
import java.io.File

@Suppress("unused")
object PixivBackupCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "backup",
    description = "PIXIV备份指令"
) {

    private var compressJob: Job? = null

    private var panJob: Job? = null

    private fun CommandSender.compress(block: PixivZipper.() -> List<File>) {
        check(compressJob?.isActive != true) { "其他任务正在压缩中, ${compressJob}..." }
        compressJob = PixivHelperPlugin.async(Dispatchers.IO) {
            PixivZipper.block().forEach { file ->
                if (this@compress is MemberCommandSenderOnMessage) {
                    sendMessage("${file.name} 压缩完毕，开始上传到群文件")
                    runCatching {
                        group.sendFile(path = file.name, file = file)
                    }.onFailure {
                        sendMessage("上传失败: ${it.message}")
                    }
                } else {
                    sendMessage("${file.name} 压缩完毕，开始上传到百度云")
                    runCatching {
                        BaiduNetDiskUpdater.uploadFile(file)
                    }.onSuccess {
                        val code = file.getRapidUploadInfo().format()
                        logger.info { "[${file.name}]上传成功: 百度云标准码${code} " }
                        sendMessage("[${file.name}]上传成功: $code")
                    }.onFailure {
                        logger.warning({ "[${file.name}]上传失败" }, it)
                        sendMessage("[${file.name}]上传失败, ${it.message}")
                    }
                }
            }
        }
    }

    private fun CommandSender.pan(block: suspend BaiduNetDiskUpdater.() -> Unit) {
        check(panJob?.isActive != true) { "其他任务正在运行中, ${panJob}..." }
        panJob = PixivHelperPlugin.async(Dispatchers.IO) {
            BaiduNetDiskUpdater.block()
        }
    }

    @SubCommand
    @Description("备份指定用户的作品")
    fun CommandSender.user(uid: Long) = compress {
        compressArtWorks(list = useMappers { it.artwork.userArtWork(uid) }, basename = "USER[${uid}]").let(::listOf)
    }

    @SubCommand
    @Description("备份已设定别名用户的作品")
    fun CommandSender.alias() = compress {
        useMappers { it.statistic.alias() }.map { it.uid }.toSet().map { uid ->
            compressArtWorks(list = useMappers { it.artwork.userArtWork(uid) }, basename = "USER[${uid}]")
        }
    }

    @SubCommand
    @Description("备份指定标签的作品")
    fun CommandSender.tag(tag: String, bookmark: Long = 0) = compress {
        compressArtWorks(list = useMappers { it.artwork.findByTag(tag, bookmark) }, basename = "TAG[${tag}]").let(::listOf)
    }

    @SubCommand
    @Description("备份插件数据")
    fun CommandSender.data() = compress {
        compressData(list = getBackupList())
    }

    @SubCommand
    @Description("列出备份目录")
    suspend fun CommandSender.list() {
        sendMessage(buildMessageChain {
            PixivZipper.listZipFiles().forEach { file ->
                appendLine(file.name)
            }
        })
    }

    @SubCommand
    @Description("列出备份目录")
    suspend fun MemberCommandSenderOnMessage.get(name: String) {
        PixivZipper.listZipFiles().find { file ->
            file.name == name || file.nameWithoutExtension == name
        }.let {
            requireNotNull(it) { "文件 [${name}] 不存在" }
        }.let { file ->
            runCatching {
                group.sendFile(path = file.name, file = file)
            }.onFailure {
                sendMessage("上传失败: ${it.message}")
            }
        }
    }

    @SubCommand
    @Description("上传插件数据到百度云")
    fun CommandSender.upload(file: String) = pan {
        val source = PixivHelperSettings.backupFolder.resolve(file)
        val code = source.getRapidUploadInfo().format()
        runCatching {
            uploadFile(file = source)
        }.onSuccess { info ->
            logger.info { "[${file}]($code)上传成功: $info" }
            sendMessage("[${file}]($code)上传成功: $info")
        }.onFailure {
            logger.warning({ "[${file}]上传失败" }, it)
            sendMessage("[${file}]上传失败")
        }
    }

    @SubCommand
    @Description("百度云用户认证")
    fun CommandSender.auth(code: String) = pan {
        runCatching {
            getAuthorizeToken(code = code)
        }.onFailure {
            logger.warning({ "认证失败, code: $code" }, it)
            sendMessage("认证失败, code: $code")
        }.onSuccess { token ->
            saveToken(token = token)
            logger.info { "认证成功, $token" }
            sendMessage("认证成功, $token")
        }
    }
}