package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.tools.PixivZipper

@Suppress("unused")
object PixivBackupCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "backup",
    description = "PIXIV备份指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private var userJob: Job? = null

    private var dataJob: Job? = null

    @SubCommand
    fun ConsoleCommandSender.user(uid: Long) {
        check(userJob?.isActive != true) { "正在压缩中, ${userJob}..." }
        userJob = PixivHelperPlugin.async(Dispatchers.IO) {
            PixivZipper.compressArtWorks(useArtWorkInfoMapper { it.userArtWork(uid) }, "USER[${uid}]")
        }
    }

    @SubCommand
    fun ConsoleCommandSender.data() {
        check(dataJob?.isActive != true) { "正在压缩中, ${dataJob}..." }
        dataJob = PixivHelperPlugin.async(Dispatchers.IO) {
            PixivZipper.backupData()
        }
    }
}