package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadTo
import xyz.cssxsh.baidu.oauth.*
import xyz.cssxsh.baidu.getRapidUploadInfo
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.tools.*

@Suppress("unused")
object PixivBackupCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "backup",
    description = "PIXIV备份指令"
) {

    private var compressJob: Job? = null

    private var panJob: Job? = null

    @SubCommand
    @Description("备份指定用户的作品")
    fun CommandSender.user(uid: Long) {
        check(compressJob?.isActive != true) { "其他任务正在压缩中, ${compressJob}..." }
        compressJob = PixivHelperPlugin.async(Dispatchers.IO) {
            PixivZipper.compressArtWorks(list = useMappers { it.artwork.userArtWork(uid) }, basename = "USER[${uid}]").let {
                sendMessage("${it.name} 压缩完毕")
            }
        }
    }

    @SubCommand
    @Description("备份已设定别名用户的作品")
    fun CommandSender.alias() {
        check(compressJob?.isActive != true) { "其他任务正在压缩中, ${compressJob}..." }
        compressJob = PixivHelperPlugin.async(Dispatchers.IO) {
            PixivAliasData.aliases.values.toSet().forEach { uid ->
                PixivZipper.compressArtWorks(list = useMappers { it.artwork.userArtWork(uid) }, basename = "USER[${uid}]").let {
                    sendMessage("${it.name} 压缩完毕")
                }
            }
        }
    }

    @SubCommand
    @Description("备份指定标签的作品")
    fun CommandSender.tag(tag: String) {
        check(compressJob?.isActive != true) { "其他任务正在压缩中, ${compressJob}..." }
        compressJob = PixivHelperPlugin.async(Dispatchers.IO) {
            TODO(tag)
        }
    }

    @SubCommand
    @Description("备份插件数据")
    fun CommandSender.data() {
        check(compressJob?.isActive != true) { "其他任务正在压缩中, ${compressJob}..." }
        compressJob = PixivHelperPlugin.async(Dispatchers.IO) {
            PixivZipper.compressData(list = getBackupList())
            sendMessage("数据备份 压缩完毕")
        }
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
            sendMessage(file.uploadTo(fromEvent.group, file.name))
        }
    }

    @SubCommand
    @Description("上传插件数据到百度云")
    fun CommandSender.upload(file: String) {
        check(panJob?.isActive != true) { "其他任务正在运行中, ${panJob}..." }
        panJob = PixivHelperPlugin.async(Dispatchers.IO) {
            val source = PixivHelperSettings.backupFolder.resolve(file)
            check(source.isFile) { "[${file}]不是文件或不存在" }
            val code = source.getRapidUploadInfo().format()
            runCatching {
                BaiduNetDiskUpdater.uploadFile(file = source)
            }.onSuccess { info ->
                logger.info { "[${file}]($code)上传成功: $info" }
                sendMessage("[${file}]($code)上传成功: $info")
            }.onFailure {
                logger.warning({ "[${file}]上传失败" }, it)
                sendMessage("[${file}]上传失败")
            }
        }
    }

    @SubCommand
    @Description("百度云用户认证")
    fun CommandSender.auth(code: String) {
        check(panJob?.isActive != true) { "其他任务正在运行中, ${panJob}..." }
        panJob = PixivHelperPlugin.async(Dispatchers.IO) {
            runCatching {
                BaiduNetDiskUpdater.getAuthorizeToken(code = code)
            }.onFailure {
                logger.warning({ "认证失败, code: $code" }, it)
                sendMessage("认证失败, code: $code")
            }.onSuccess { token ->
                BaiduNetDiskUpdater.saveToken(token = token)
                logger.info { "认证成功, $token" }
                sendMessage("认证成功, $token")
            }
        }
    }
}