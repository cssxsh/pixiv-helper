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
}