@file:Suppress("unused")
package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
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

    private suspend fun PixivHelper.cacheRank(): Int = RankMode.values().map {
        async { illustRanking(mode = it) }
    }.awaitAll().flatMap {
        it.illusts
    }.count {
        it.pid !in PixivCacheData.illust && runCatching { getImages(it) }.isSuccess
    }

    private suspend fun PixivHelper.cacheFollow(): Int = illustFollow().illusts.count {
        it.pid !in PixivCacheData.illust && runCatching { getImages(it) }.isSuccess
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.all() = getHelper().runCatching {
        cacheRank() + cacheFollow()
    }.onSuccess {
        quoteReply("缓存完毕共${it}个新作品")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.check() = getHelper().runCatching {
        PixivCacheData.illust.count { (pid, illust) ->
            val dir = PixivHelperPlugin.imagesFolder(illust.pid)
            illust.getImageUrls().flatMap { fileUrls ->
                fileUrls.filter { "origin" in it.key }.values
            }.runCatching {
                forEachIndexed { index, _ ->
                    val name = "${illust.pid}-origin-${index}.jpg"
                    File(dir, name).also {
                        require(it.canRead()) {
                            "${it.name} 不可读， 文件将删除，结果：${it.delete()}"
                        }
                    }
                }
            }.onFailure {
                PixivHelperPlugin.logger.verbose("${pid}缓存出错: ${it.message}")
                PixivCacheData.illust.remove(pid)
            }.isSuccess
        }
    }.onSuccess {
        quoteReply("检查缓存完毕，完整度: ${it}/${PixivCacheData.illust.size}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}