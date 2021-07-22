package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import java.time.OffsetDateTime

object PixivDeleteCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "delete",
    description = "PIXIV删除指令",
    overrideContext = PixivCommandArgumentContext
) {

    private fun ArtWorkInfo.delete(): Boolean {
        return PixivHelperSettings.imagesFolder(pid).listFiles { file ->
            file.isFile && file.extension != "json"
        }?.all { it.delete() } ?: true
    }

    @SubCommand
    @Description("删除指定作品")
    suspend fun CommandSender.artwork(pid: Long, record: Boolean = false) {
        logger.info { "作品(${pid})信息将从缓存移除" }
        if (record) useMappers { mappers ->
            mappers.artwork.deleteByPid(pid = pid, comment = "command delete artwork in ${OffsetDateTime.now()}")
        }
        sendMessage("作品(${pid})图片将删除，结果${EmptyArtWorkInfo.copy(pid = pid).delete()}")
    }

    @SubCommand
    @Description("删除指定用户作品")
    suspend fun CommandSender.user(uid: Long, record: Boolean = false) {
        val artworks = useMappers { it.artwork.userArtWork(uid) }
        if (record) useMappers { mappers ->
            mappers.artwork.deleteByUid(uid = uid, comment = "command delete user in ${OffsetDateTime.now()}")
        }
        sendMessage("USER(${uid})共${artworks.size}个作品需要删除")
        artworks.forEach {
            logger.info { "作品(${it.pid})信息将从缓存移除" }
            it.delete()
        }
        sendMessage("删除完毕")
    }

    private const val OFFSET_MAX = 99_999_999L

    private const val OFFSET_STEP = 1_000_000L

    private val ranges = (0 until OFFSET_MAX step OFFSET_STEP).map { offset -> offset until (offset + OFFSET_STEP) }

    @SubCommand
    @Description("删除小于指定收藏数作品")
    suspend fun CommandSender.bookmarks(min: Long, record: Boolean = false) {
        ranges.forEach { interval ->
            logger.verbose { "开始检查[$interval]" }
            val artworks = useMappers { mappers ->
                mappers.artwork.artworks(interval).filter { it.totalBookmarks < min }
            }
            if (artworks.isEmpty()) return@forEach
            sendMessage("{$min}(${interval})共${artworks.size}个作品需要删除")
            useMappers { mappers ->
                artworks.forEach {
                    logger.info { "作品(${it.pid})信息将从缓存移除" }
                    if (record) mappers.artwork.deleteByPid(
                        pid = it.pid,
                        comment = "command delete bookmarks $min in ${OffsetDateTime.now()}"
                    )
                    it.delete()
                }
            }
            sendMessage("删除完毕")
        }
    }

    @SubCommand
    @Description("删除大于指定页数作品（用于处理漫画作品）")
    suspend fun CommandSender.page(max: Int, record: Boolean = false) {
        ranges.forEach { interval ->
            logger.verbose { "开始检查[$interval]" }
            val artworks = useMappers { mappers ->
                mappers.artwork.artworks(interval).filter { it.pageCount > max }
            }
            if (artworks.isEmpty()) return@forEach
            sendMessage("[$max](${interval})共${artworks.size}个作品需要删除")
           useMappers { mappers ->
                artworks.forEach {
                    logger.info { "作品(${it.pid})信息将从缓存移除" }
                    if (record) mappers.artwork.deleteByPid(
                        pid = it.pid,
                        comment = "command delete page_count $max in ${OffsetDateTime.now()}"
                    )
                    it.delete()
                }
            }
            sendMessage("删除完毕")
        }
    }
}