package xyz.cssxsh.mirai.pixiv.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.pixiv.*
import java.time.*

public object PixivDeleteCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "delete",
    description = "PIXIV删除指令"
), PixivHelperCommand {

    private fun delete(artwork: ArtWorkInfo): Boolean {
        if (artwork.type == WorkContentType.UGOIRA.ordinal) {
            UgoiraImagesFolder.resolve("${artwork.pid}.gif").delete()
        }
        return images(artwork.pid).deleteRecursively()
    }

    @SubCommand
    @Description("删除指定作品")
    public suspend fun CommandSender.artwork(pid: Long, record: Boolean = false) {
        logger.info { "作品(${pid})信息将从缓存移除" }
        if (record) ArtWorkInfo.delete(pid = pid, comment = "command delete artwork in ${OffsetDateTime.now()}")
        val artwork = ArtWorkInfo[pid]
        if (artwork == null) {
            sendMessage("作品(${pid})不在数据库缓存中")
            return
        }
        sendMessage("作品(${pid})[${artwork.author.uid}]图片将删除，结果${delete(artwork)}")
    }

    @SubCommand
    @Description("删除指定用户作品")
    public suspend fun CommandSender.user(uid: Long, record: Boolean = false) {
        val artworks = ArtWorkInfo.user(uid)
        if (record) ArtWorkInfo.deleteUser(uid = uid, comment = "command delete artwork in ${OffsetDateTime.now()}")
        sendMessage("USER(${uid})共${artworks.size}个作品需要删除")
        for (artwork in artworks) {
            logger.info { "作品(${artwork.pid})[${artwork.author.uid}]信息将从缓存移除" }
            delete(artwork)
        }
        sendMessage("删除完毕")
    }

    @SubCommand
    @Description("删除小于指定收藏数作品")
    public suspend fun CommandSender.bookmarks(min: Long, record: Boolean = false) {
        for (range in ALL_RANGE) {
            if (isActive.not()) break
            val artworks = ArtWorkInfo.interval(range, min, 0)
            if (artworks.isEmpty()) continue
            logger.info { "{$min}(${range})共${artworks.size}个作品需要删除" }
            val comment = "command delete bookmarks $min in ${OffsetDateTime.now()}"
            for (artwork in artworks) {
                logger.verbose { "作品(${artwork.pid})[${artwork.author.uid}]信息将从缓存移除" }
                if (record) ArtWorkInfo.delete(pid = artwork.pid, comment = comment)
                delete(artwork)
            }
            sendMessage("删除完毕")
        }
    }

    @SubCommand
    @Description("删除大于指定页数作品（用于处理漫画作品）")
    public suspend fun CommandSender.page(max: Int, record: Boolean = false) {
        for (range in ALL_RANGE) {
            if (isActive.not()) break
            val artworks = ArtWorkInfo.interval(range, Long.MAX_VALUE, max)
            if (artworks.isEmpty()) continue
            logger.info { "[$max](${range})共${artworks.size}个作品需要删除" }
            val comment = "command delete page_count $max in ${OffsetDateTime.now()}"
            for (artwork in artworks) {
                logger.verbose { "作品(${artwork.pid})[${artwork.author.uid}]信息将从缓存移除" }
                if (record) ArtWorkInfo.delete(pid = artwork.pid, comment = comment)
                delete(artwork)
            }
        }
        sendMessage("删除完毕")
    }

    @SubCommand
    @Description("删除 漫画")
    public suspend fun CommandSender.manga(record: Boolean = false) {
        for (range in ALL_RANGE) {
            if (isActive.not()) break
            val artworks = ArtWorkInfo.type(range, WorkContentType.MANGA)
            if (artworks.isEmpty()) continue
            logger.info { "[manga](${range})共${artworks.size}个作品需要删除" }
            val comment = "command delete manga in ${OffsetDateTime.now()}"
            for (artwork in artworks) {
                logger.verbose { "作品(${artwork.pid})[${artwork.author.uid}]信息将从缓存移除" }
                if (record) ArtWorkInfo.delete(pid = artwork.pid, comment = comment)
                delete(artwork)
            }
        }
        sendMessage("删除完毕")
    }

    @SubCommand
    @Description("删除 已被记录删除作品")
    public suspend fun CommandSender.record() {
        for (range in ALL_RANGE) {
            if (isActive.not()) break
            val artworks = ArtWorkInfo.deleted(range)
            if (artworks.isEmpty()) continue
            logger.info { "[record](${range})共${artworks.size}个作品需要删除" }
            for (artwork in artworks) {
                logger.verbose { "作品(${artwork.pid})[${artwork.author.uid}]信息将从缓存移除" }
                delete(artwork)
            }
        }
        sendMessage("删除完毕")
    }
}