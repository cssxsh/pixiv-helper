package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.pixiv.RankMode
import xyz.cssxsh.pixiv.api.app.illustFollow
import xyz.cssxsh.pixiv.api.app.illustRanking

object PixivCache : SimpleCommand(
    PixivHelperPlugin,
    "cache", "缓存",
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


    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle() = getHelper().runCatching {
        cacheRank() + cacheFollow()
    }.onSuccess {
        quoteReply("缓存完毕共${it}个新作品")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}