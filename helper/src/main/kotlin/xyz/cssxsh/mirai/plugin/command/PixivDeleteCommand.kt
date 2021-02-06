package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.command.descriptor.CommandValueArgumentParser
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.command.descriptor.buildCommandArgumentContext
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.verbose
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.imagesFolder
import xyz.cssxsh.mirai.plugin.useArtWorkInfoMapper
import xyz.cssxsh.pixiv.WorkContentType
import xyz.cssxsh.pixiv.model.ArtWorkInfo

@Suppress("unused")
object PixivDeleteCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "delete",
    description = "PIXIV删除指令",
    overrideContext = buildCommandArgumentContext {
        WorkContentType::class with object : CommandValueArgumentParser<WorkContentType> {
            override fun parse(raw: String, sender: CommandSender): WorkContentType =
                WorkContentType.valueOf(raw.toUpperCase())
        }
    }
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private fun deleteArtwork(pid: Long) {
        useArtWorkInfoMapper { it.deleteByPid(pid) }
        imagesFolder(pid).apply {
            listFiles()?.forEach {
                it.delete()
            }
            logger.verbose { "作品(${pid})文件夹将删除，结果${delete()}" }
        }
    }

    private fun List<ArtWorkInfo>.delete() {
        useArtWorkInfoMapper { mapper ->
            forEach { info ->
                logger.info { "作品(${info.pid})[${info.type}][${info.title}]{${info.totalBookmarks}}信息将从缓存移除" }
                mapper.deleteByPid(info.pid)
            }
        }
        forEach { info ->
            imagesFolder(info.pid).apply {
                listFiles()?.forEach {
                    it.delete()
                }
                logger.verbose { "作品(${info.pid})文件夹将删除，结果${delete()}" }
            }
        }
    }

    @SubCommand
    fun ConsoleCommandSender.artwork(pid: Long) {
        logger.info { "作品(${pid})信息将从缓存移除" }
        deleteArtwork(pid)
    }

    @SubCommand
    fun ConsoleCommandSender.user(uid: Long) {
        useArtWorkInfoMapper { it.userArtWork(uid) }.also {
            logger.verbose { "USER(${uid})共${it.size}个作品需要删除" }
        }.delete()
    }

    private const val OFFSET_MAX = 99_999_999L

    private const val OFFSET_STEP = 1_000_000L

    @SubCommand
    fun ConsoleCommandSender.type(type: WorkContentType, bookmarks: Long) {
        (0 until OFFSET_MAX step OFFSET_STEP).map { offset -> offset until (offset + OFFSET_STEP) }.forEach { interval ->
            logger.verbose { "开始检查[$interval]" }
            useArtWorkInfoMapper { it.artWorks(interval) }.filter {
                WorkContentType.valueOf(it.type.trim().toUpperCase()) == type && it.totalBookmarks < bookmarks
            }.also {
                logger.verbose { "TYPE[$type]{$bookmarks}(${interval})共${it.size}个作品需要删除" }
            }.delete()
        }
    }
}