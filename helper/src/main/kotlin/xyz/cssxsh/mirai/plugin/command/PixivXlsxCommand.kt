package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Deferred
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.tools.PoiTool
import java.io.File

@Suppress("unused")
object PixivXlsxCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "cache",
    description = "PIXIV压缩指令"
) {

    private var xlsxJob: Deferred<File>? = null

    /**
     * cache
     */
    @SubCommand
    fun ConsoleCommandSender.cache() {
        check(xlsxJob?.isActive != true) { "正在制作XLSX中, ${xlsxJob}..." }
        xlsxJob = PoiTool.saveCacheAsync()
    }
}