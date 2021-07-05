package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import java.time.OffsetDateTime

object PixivDeleteCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "delete",
    description = "PIXIV删除指令",
    overrideContext = PixivCommandArgumentContext
) {

    private fun deleteImage(pid: Long): Boolean {
        return PixivHelperSettings.imagesFolder(pid).listFiles { file ->
            file.isFile && file.extension != "json"
        }?.all { it.delete() } ?: true
    }

    @SubCommand
    @Description("删除指定作品")
    suspend fun CommandSender.artwork(pid: Long) {
        logger.info { "作品(${pid})信息将从缓存移除" }
        useMappers {
            it.artwork.deleteByPid(pid = pid, comment = "command delete artwork in ${OffsetDateTime.now()}")
        }
        logger.info { "作品(${pid})信息将从缓存移除" }
        sendMessage("作品(${pid})图片将删除，结果${deleteImage(pid)}")
    }

    @SubCommand
    @Description("删除指定用户作品")
    suspend fun CommandSender.user(uid: Long) {
        val artworks = useMappers { it.artwork.userArtWork(uid) }
        useMappers { mappers ->
            mappers.artwork.deleteByUid(uid = uid, comment = "command delete user in ${OffsetDateTime.now()}")
        }
        sendMessage("USER(${uid})共${artworks.size}个作品需要删除")
        artworks.forEach {
            logger.info { "作品(${it.pid})信息将从缓存移除" }
            deleteImage(it.pid)
        }
        sendMessage("删除完毕")
    }

    private const val OFFSET_MAX = 99_999_999L

    private const val OFFSET_STEP = 1_000_000L

    private val ranges = (0 until OFFSET_MAX step OFFSET_STEP).map { offset -> offset until (offset + OFFSET_STEP) }

    @SubCommand
    @Description("删除小于指定收藏数作品（用于处理漫画作品）")
    suspend fun CommandSender.bookmarks(bookmarks: Long) {
        ranges.forEach { interval ->
            logger.verbose { "开始检查[$interval]" }
            val artworks = useMappers { mappers ->
                mappers.artwork.artworks(interval).filter { it.totalBookmarks < bookmarks }
            }
            sendMessage("{<$bookmarks}(${interval})共${artworks.size}个作品需要删除")
            artworks.forEach {
                logger.info { "作品(${it.pid})信息将从缓存移除" }
                useMappers { mappers ->
                    mappers.artwork.deleteByPid(
                        pid = it.pid,
                        comment = "command delete bookmarks $bookmarks in ${OffsetDateTime.now()}"
                    )
                }
            }
            sendMessage("删除完毕")
        }
    }
}