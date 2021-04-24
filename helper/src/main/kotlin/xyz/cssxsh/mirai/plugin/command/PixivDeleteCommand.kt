package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import java.time.OffsetDateTime

@Suppress("unused")
object PixivDeleteCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "delete",
    description = "PIXIV删除指令",
    overrideContext = PixivCommandArgumentContext
) {

    private fun deleteImage(pid: Long) = PixivHelperSettings.imagesFolder(pid).listFiles { file ->
        file.isFile && file.extension != "json"
    }?.all { it.delete() }

    @SubCommand
    @Description("删除指定作品")
    fun ConsoleCommandSender.artwork(pid: Long) {
        logger.info { "作品(${pid})信息将从缓存移除" }
        useMappers { mappers ->
            mappers.artwork.deleteByPid(
                pid = pid,
                comment = "command delete artwork in ${OffsetDateTime.now()}"
            )
        }
        logger.verbose { "作品(${pid})图片将删除，结果${deleteImage(pid)}" }
    }

    @SubCommand
    @Description("删除指定用户作品")
    fun ConsoleCommandSender.user(uid: Long) {
        useMappers { mappers ->
            mappers.artwork.userArtWork(uid).also {
                logger.verbose { "USER(${uid})共${it.size}个作品需要删除" }
                mappers.artwork.deleteByUid(uid = uid, comment = "command delete user in ${OffsetDateTime.now()}")
            }
        }.forEach {
            logger.verbose { "作品(${it.pid})图片将删除，结果${deleteImage(it.pid)}" }
        }
    }

    private const val OFFSET_MAX = 99_999_999L

    private const val OFFSET_STEP = 1_000_000L

    private val ranges = (0 until OFFSET_MAX step OFFSET_STEP).map { offset -> offset until (offset + OFFSET_STEP) }

    @SubCommand
    @Description("删除小于指定收藏数作品（用于处理漫画作品）")
    fun ConsoleCommandSender.bookmarks(bookmarks: Long) {
        ranges.forEach { interval ->
            logger.verbose { "开始检查[$interval]" }
            useMappers { mappers ->
                mappers.artwork.artworks(interval).filter { it.totalBookmarks < bookmarks }.also {
                    logger.verbose { "{<$bookmarks}(${interval})共${it.size}个作品需要删除" }
                }.forEach {
                    logger.info { "作品(${it.pid})信息将从缓存移除" }
                    mappers.artwork.deleteByPid(
                        pid = it.pid,
                        comment = "command delete bookmarks $bookmarks in ${OffsetDateTime.now()}"
                    )
                    logger.verbose { "作品(${it.pid})图片将删除，结果${deleteImage(it.pid)}" }
                }
            }
        }
    }
}