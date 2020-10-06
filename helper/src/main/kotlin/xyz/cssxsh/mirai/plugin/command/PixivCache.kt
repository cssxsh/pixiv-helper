@file:Suppress("unused")
package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.pixiv.RankMode
import xyz.cssxsh.pixiv.api.app.illustFollow
import xyz.cssxsh.pixiv.api.app.illustRanking
import java.io.File

object PixivCache : CompositeCommand(
    PixivHelperPlugin,
    "cache",
    description = "缓存指令",
    prefixOptional = true
) {

    private suspend fun PixivHelper.cacheRank(): Int = RankMode.values().map { mode ->
        illustRanking(mode = mode).illusts.count { info ->
            info.pid !in PixivCacheData.illusts && runCatching { getImages(info) }.isSuccess
        }
    }.sum()

    private suspend fun PixivHelper.cacheFollow(): Int = illustFollow().illusts.count { info ->
        info.pid !in PixivCacheData.illusts && runCatching { getImages(info) }.isSuccess
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.all() = getHelper().runCatching {
        cacheFollow() + cacheRank()
    }.onSuccess {
        quoteReply("缓存完毕共${it}个新作品")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.check() = getHelper().runCatching {
        PixivCacheData.illusts.filter { (pid, illust) ->
            val dir = PixivHelperPlugin.imagesFolder(illust.pid)
            illust.getImageUrls().flatMap { fileUrls ->
                fileUrls.filter { "origin" in it.key }.values
            }.runCatching {
                forEachIndexed { index, _ ->
                    val name = "${illust.pid}-origin-${index}.jpg"
                    File(dir, name).also {
                        require(it.canRead()) {
                            "${it.name} 不可读， 文件将删除，结果：${dir.delete()}"
                        }
                    }
                }
            }.onFailure {
                PixivHelperPlugin.logger.verbose("${pid}缓存出错: ${it.message}")
            }.isFailure
        }
    }.onSuccess {
        quoteReply("检查缓存完毕，错误率: ${it.size}/${PixivCacheData.illusts.size}")
        it.forEach { (pid, _) ->
            PixivCacheData.illusts.remove(pid)
        }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess


    /**
     * 设置缓存目录 cache set /storage/emulated/0/PixivCache
     * @param path 缓存目录
     */
    @SubCommand
    fun ConsoleCommandSender.set(path: String) {
        runCatching {
            File(PixivHelperSettings.cachePath).renameTo(File(path))
        }
        PixivHelperSettings.cachePath = path
    }
}