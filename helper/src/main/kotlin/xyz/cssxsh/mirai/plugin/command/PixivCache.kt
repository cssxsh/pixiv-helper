@file:Suppress("unused")
package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.delay
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
), PixivHelperLogger {
    /**
     * timeMillis
      */
    var delayTime = 1_000L

    private suspend fun PixivHelper.cacheRank(): Int = RankMode.values().map { mode ->
        illustRanking(mode = mode).illusts.count { info ->
            info.pid !in PixivCacheData && runCatching {
                getImages(info)
                delay(delayTime)
            }.onFailure {
                logger.verbose("获取图片${info.pid}错误", it)
            }.isSuccess
        }
    }.sum()

    private suspend fun PixivHelper.cacheFollow(): Int = illustFollow().illusts.count { info ->
        info.pid !in PixivCacheData && runCatching {
            getImages(info)
            delay(delayTime)
        }.onFailure {
            logger.verbose("获取图片错误", it)
        }.isSuccess
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
        PixivCacheData.values.also { list ->
            logger.verbose("共有 ${list.size} 个作品需要检查")
        }.filter { illust ->
            val dir = PixivHelperPlugin.imagesFolder(illust.pid)
            (0 until illust.pageCount).runCatching {
                forEachIndexed { index, _ ->
                    val name = "${illust.pid}-origin-${index}.jpg"
                    File(dir, name).also {
                        require(it.canRead()) {
                            "${it.name} 不可读， 文件将删除，结果：${dir.delete()}"
                        }
                    }
                }
            }.onFailure {
                logger.verbose("${illust.pid}缓存出错: ${it.message}")
            }.isFailure
        }
    }.onSuccess {
        quoteReply("检查缓存完毕，错误率: ${it.size}/${PixivCacheData.values.size}")
        it.forEach { illust ->
            PixivCacheData.remove(illust.pid)
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