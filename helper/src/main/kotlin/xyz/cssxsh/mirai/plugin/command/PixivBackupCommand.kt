package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.tools.PixivZipper
import java.io.File

@Suppress("unused")
object PixivBackupCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "backup",
    description = "PIXIV备份指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private var compressJob: Deferred<File>? = null

    private var backupJob: Deferred<List<File>>? = null

    @SubCommand
    fun ConsoleCommandSender.user(uid: Long) {
        check(compressJob?.isActive != true) { "正在压缩中, ${compressJob}..." }
        compressJob = PixivHelperPlugin.async(Dispatchers.IO) {
            PixivZipper.compressArtWorks(useArtWorkInfoMapper { it.userArtWork(uid) }, "USER[${uid}]")
        }
    }

    @SubCommand
    fun ConsoleCommandSender.backup() {
        check(backupJob?.isActive != true) { "正在压缩中, ${backupJob}..." }
        backupJob = PixivHelperPlugin.async(Dispatchers.IO) {
            PixivZipper.backupData()
        }
    }
}