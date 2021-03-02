package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.utils.*
import xyz.cssxsh.baidu.oauth.*
import xyz.cssxsh.baidu.getRapidUploadInfo
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.PixivAliasData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
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
    fun ConsoleCommandSender.user(uid: Long) {
        check(compressJob?.isActive != true) { "其他任务正在压缩中, ${compressJob}..." }
        compressJob = PixivHelperPlugin.async(Dispatchers.IO) {
            PixivZipper.compressArtWorks(list = useArtWorkInfoMapper { it.userArtWork(uid) }, basename = "USER[${uid}]")
        }
    }

    @SubCommand
    fun ConsoleCommandSender.alias() {
        check(compressJob?.isActive != true) { "其他任务正在压缩中, ${compressJob}..." }
        compressJob = PixivHelperPlugin.async(Dispatchers.IO) {
            PixivAliasData.aliases.values.toSet().forEach { uid ->
                PixivZipper.compressArtWorks(list = useArtWorkInfoMapper { it.userArtWork(uid) }, basename = "USER[${uid}]")
            }
        }
    }

    @SubCommand
    fun ConsoleCommandSender.tag(tag: String) {
        check(compressJob?.isActive != true) { "其他任务正在压缩中, ${compressJob}..." }
        compressJob = PixivHelperPlugin.async(Dispatchers.IO) {
            TODO(tag)
        }
    }

    @SubCommand
    fun ConsoleCommandSender.data() {
        check(compressJob?.isActive != true) { "其他任务正在压缩中, ${compressJob}..." }
        compressJob = PixivHelperPlugin.async(Dispatchers.IO) {
            PixivZipper.compressData(list = getBackupList())
        }
    }

    @SubCommand
    fun ConsoleCommandSender.upload(file: String) {
        check(panJob?.isActive != true) { "其他任务正在运行中, ${panJob}..." }
        panJob = PixivHelperPlugin.async(Dispatchers.IO) {
            val source = PixivHelperSettings.backupFolder.resolve(file)
            check(source.isFile) { "[${file}]不是文件或不存在" }
            val code = source.getRapidUploadInfo().format()
            runCatching {
                BaiduNetDiskUpdater.uploadFile(file = source)
            }.onSuccess { info ->
                logger.info { "[${file}]($code)上传成功: $info" }
            }.onFailure {
                logger.warning({ "[${file}]上传失败" }, it)
            }
        }
    }

    @SubCommand
    fun ConsoleCommandSender.auth(code: String) {
        check(panJob?.isActive != true) { "其他任务正在运行中, ${panJob}..." }
        panJob = PixivHelperPlugin.async(Dispatchers.IO) {
            runCatching {
                BaiduNetDiskUpdater.getAuthorizeToken(code = code)
            }.onFailure {
                logger.warning({ "认证失败, code: $code" }, it)
            }.onSuccess { token ->
                BaiduNetDiskUpdater.saveToken(token = token)
                logger.info { "认证成功, $token" }
            }
        }
    }
}