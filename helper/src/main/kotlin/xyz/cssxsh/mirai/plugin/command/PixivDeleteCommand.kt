package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.model.*

@Suppress("unused")
object PixivDeleteCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "delete",
    description = "PIXIV删除指令",
    overrideContext = PixivCommandArgumentContext
) {

    private fun deleteArtwork(pid: Long, comment: String) {
        useMappers { mappers ->
            mappers.artwork.deleteByPid(pid)
            mappers.delete.add(pid = pid, comment = comment)
        }
        PixivHelperSettings.imagesFolder(pid).apply {
            listFiles()?.forEach {
                it.delete()
            }
            logger.verbose { "作品(${pid})文件夹将删除，结果${delete()}" }
        }
    }

    private fun List<ArtWorkInfo>.delete(comment: String) {
        useMappers {  mappers ->
            forEach { info ->
                logger.info { "作品(${info.pid})[${info.type}][${info.title}]{${info.totalBookmarks}}信息将从缓存移除" }
                mappers.artwork.deleteByPid(info.pid)
                mappers.delete.add(pid = info.pid, comment = comment)
            }
        }
        forEach { info ->
            PixivHelperSettings.imagesFolder(info.pid).apply {
                listFiles()?.forEach {
                    it.delete()
                }
                logger.verbose { "作品(${info.pid})文件夹将删除，结果${delete()}" }
            }
        }
    }

    @SubCommand
    @Description("删除指定作品")
    fun ConsoleCommandSender.artwork(pid: Long) {
        logger.info { "作品(${pid})信息将从缓存移除" }
        deleteArtwork(pid = pid, comment = "command delete artwork")
    }

    @SubCommand
    @Description("删除指定用户作品")
    fun ConsoleCommandSender.user(uid: Long) {
        useMappers { it.artwork.userArtWork(uid) }.also {
            logger.verbose { "USER(${uid})共${it.size}个作品需要删除" }
        }.delete(comment = "command delete user $uid")
    }

    private const val OFFSET_MAX = 99_999_999L

    private const val OFFSET_STEP = 1_000_000L

    private val ranges = (0 until OFFSET_MAX step OFFSET_STEP).map { offset -> offset until (offset + OFFSET_STEP) }

    @SubCommand
    @Description("删除小于指定收藏数作品（用于处理漫画作品）")
    fun ConsoleCommandSender.bookmarks(bookmarks: Long) {
        ranges.forEach { interval ->
            logger.verbose { "开始检查[$interval]" }
            useMappers { it.artwork.artWorks(interval) }.filter {
                it.totalBookmarks < bookmarks
            }.also {
                logger.verbose { "{$bookmarks}(${interval})共${it.size}个作品需要删除" }
            }.delete(comment = "command delete bookmarks $bookmarks")
        }
    }
}