package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsFile
import xyz.cssxsh.baidu.oauth.*
import xyz.cssxsh.baidu.getRapidUploadInfo
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.tools.*
import java.io.File

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
                        sendMessage(file.toExternalResource().uploadAsFile(contact = group, path = file.name))
                    }.onFailure {
                        sendMessage("[${file.name}]上传失败: ${it.message}")
                    }
                } else {
                    sendMessage("${file.name} 压缩完毕，开始上传到百度云")
                    val code = file.getRapidUploadInfo()
                    runCatching {
                        BaiduNetDiskUpdater.uploadFile(file)
                    }.onSuccess {
                        logger.info { "[${file.name}]上传成功，百度云标准码: ${code.format()} " }
                        sendMessage("[${file.name}]上传成功，百度云标准码: ${code.format()}")
                    }.onFailure {
                        logger.warning({ "[${file.name}]上传失败" }, it)
                        sendMessage("[${file.name}]上传失败, ${it.message}")
                    }
                }
            }
        }
    }

    private fun pan(block: suspend BaiduNetDiskUpdater.() -> Unit) {
        check(panJob?.isActive != true) { "其他任务正在运行中, ${panJob}..." }
        panJob = PixivHelperPlugin.async(Dispatchers.IO) {
            BaiduNetDiskUpdater.block()
        }
    }

    @SubCommand
    @Description("备份指定用户的作品")
    fun CommandSender.user(uid: Long) = compress {
        compressArtWorks(list = useMappers { it.artwork.userArtWork(uid) }, basename = "USER[${uid}]").let {
            listOf(it)
        }
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
        compressArtWorks(list = useMappers { it.artwork.findByTag(tag, bookmark) }, basename = "TAG[${tag}]").let {
            listOf(it)
        }
    }

    @SubCommand
    @Description("备份插件数据")
    fun CommandSender.data() = compress {
        compressData(list = getBackupList())
    }

    @SubCommand
    @Description("列出备份目录")
    suspend fun CommandSender.list() {
        sendMessage(PixivZipper.list().joinToString("\n") { file ->
            file.name
        })
    }

    @SubCommand
    @Description("获取备份文件")
    suspend fun MemberCommandSenderOnMessage.get(name: String) {
        runCatching {
            PixivZipper.find(name = name).let { file ->
                sendMessage(file.toExternalResource().uploadAsFile(contact = group, path = file.name))
            }
        }.onFailure {
            sendMessage("上传失败: ${it.message}")
        }
    }

    @SubCommand
    @Description("上传插件数据到百度云")
    fun CommandSender.upload(name: String) = pan {
        val file = PixivZipper.find(name = name)
        val code = file.getRapidUploadInfo().format()
        runCatching {
            uploadFile(file = file)
        }.onSuccess { info ->
            logger.info { "[${name}]($code)上传成功: $info" }
            sendMessage("[${name}]($code)上传成功: $info")
        }.onFailure {
            logger.warning({ "[${name}]上传失败" }, it)
            sendMessage("[${name}]上传失败, ${it.message}")
        }
    }

    @SubCommand
    @Description("百度云用户认证")
    fun CommandSender.auth(code: String) = pan {
        runCatching {
            getAuthorizeToken(code = code).also { saveToken(token = it) }
        }.onSuccess { token ->
            logger.info { "认证成功, $token" }
            sendMessage("认证成功, $token")
        }.onFailure {
            logger.warning({ "认证失败, code: $code" }, it)
            sendMessage("认证失败, ${it.message}, code: $code")
        }
    }
}