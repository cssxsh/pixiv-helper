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
import xyz.cssxsh.pixiv.api.app.illustDetail
import xyz.cssxsh.pixiv.api.app.illustFollow
import xyz.cssxsh.pixiv.api.app.illustRanking
import java.io.File

@Suppress("unused")
object PixivCache : CompositeCommand(
    PixivHelperPlugin,
    "cache",
    description = "缓存指令",
    prefixOptional = true
), PixivHelperLogger {
    /**
     * timeMillis
      */
    private var delayTime: Long
        get() = PixivHelperSettings.delayTime
        set(value) { PixivHelperSettings.delayTime = value }

    private var caching = false

    private suspend fun PixivHelper.cacheRank(): Int = RankMode.values().map { mode ->
        illustRanking(mode = mode).illusts.count { info ->
            info.pid !in PixivCacheData && runCatching {
                getImages(info)
            }.onSuccess {
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
        }.onSuccess {
            delay(delayTime)
        }.onFailure {
            logger.verbose("获取图片错误", it)
        }.isSuccess
    }

    /**
     * 缓存排行榜和关注列表最新30个作品
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.all() = getHelper().runCatching {
        require(caching.not()) { "正在缓存中..." }
        caching = true
        cacheFollow() + cacheRank()
    }.onSuccess {
        caching = false
        quoteReply("缓存完毕共${it}个新作品")
    }.onFailure {
        caching = false
        quoteReply(it.toString())
    }.isSuccess


    /**
     * 从文件夹中加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.load() = getHelper().runCatching {
        PixivHelperPlugin.cacheFolder.list { _, name ->
            name.matches("""^\d+$""".toRegex())
        }?.count { pid ->
            runCatching {
                getImages(illustDetail(pid.toLong()).illust)
            }.onSuccess {
                delay(delayTime)
            }.onFailure {
                logger.verbose("获取图片${pid}错误", it)
            }.isSuccess
        } ?: 0
    }.onSuccess {
        quoteReply("加载缓存完毕， 新增率: ${it}/${PixivCacheData.values.size}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 检查当前数据中不可读，并删除图片文件夹
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.check() = getHelper().runCatching {
        PixivCacheData.values.also { list ->
            logger.verbose("共有 ${list.size} 个作品需要检查")
        }.filter { illust ->
            val dir = PixivHelperPlugin.imagesFolder(illust.pid)
            (0 until illust.pageCount).runCatching {
                forEachIndexed { index, _ ->
                    val name = "${illust.pid}-origin-${index}.jpg"
                    File(dir, name).apply {
                        require(canRead()) {
                            "$name 不可读， 文件将删除，结果：${dir.run { 
                                listFiles()?.forEach { it.delete() }
                                delete()
                            }}"
                        }
                    }
                }
            }.onFailure {
                logger.verbose("${illust.pid}缓存出错", it)
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
    fun ConsoleCommandSender.path(path: String) {
        runCatching {
            File(PixivHelperSettings.cachePath).renameTo(File(path))
        }
        PixivHelperSettings.cachePath = path
    }

    /**
     * 设置缓存延迟时间
     */
    @SubCommand
    fun ConsoleCommandSender.delay(timeMillis: Long) {
        delayTime = timeMillis
    }

}