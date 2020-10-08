package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
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

    private var job: Job? = null

    private val isStop: Boolean get() = job?.isActive?.not() ?: true


    private suspend fun PixivHelper.getRank(modes: Array<RankMode> = RankMode.values()) = modes.map { mode ->
        async {
            illustRanking(mode = mode).illusts
        }
    }

    private suspend fun PixivHelper.getFollow(page: Int = 10) = (0 until page).map { index ->
        async {
            illustFollow(offset = index * 30L).illusts
        }
    }

    /**
     * 缓存排行榜和关注列表最新30个作品
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.all() = getHelper().runCatching {
        check(isStop) { "正在缓存中, ${job}..." }
        launch {
            runCatching {
                (getFollow() + getRank()).awaitAll().map { list ->
                    list.count { info ->
                        isActive && info.pid !in PixivCacheData && runCatching {
                            getImages(info)
                        }.onSuccess {
                            delay(delayTime)
                        }.onFailure {
                            logger.verbose("获取图片${info.pid}错误", it)
                        }.isSuccess
                    }
                }.sum()
            }.onSuccess {
                quoteReply("缓存完毕共${it}个新作品")
            }
        }
    }.onSuccess {
        job = it
        quoteReply("添加任务完成${it}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess


    /**
     * 从文件夹中加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.load() = getHelper().runCatching {
        check(isStop) { "正在缓存中, ${job}..." }
        launch {
            logger.info("从缓存目录${PixivHelperSettings.cacheFolder.absolutePath}")
            PixivHelperSettings.cacheFolder.walk().mapNotNull { file ->
                if (file.isDirectory && file.name.matches("""^[0-9]+$""".toRegex())) {
                    name.toLong()
                } else {
                    null
                }
            }.count { pid ->
                isActive && pid !in PixivCacheData && runCatching {
                    getImages(illustDetail(pid).illust)
                }.onSuccess {
                    delay(delayTime)
                }.onFailure {
                    logger.verbose("获取图片${pid}错误", it)
                }.isSuccess
            }.let {
                quoteReply("加载缓存完毕， 新增率: ${it}/${PixivCacheData.values.size}")
            }
        }
    }.onSuccess {
        job = it
        quoteReply("添加任务完成${it}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 强制停止缓存
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.cancel() = runCatching {
        job?.cancelAndJoin()
    }.onSuccess {
        quoteReply("任务${job}已停止")
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
            val dir = PixivHelperSettings.imagesFolder(illust.pid)
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
     * 设置缓存目录 cache path /storage/emulated/0/PixivCache
     * @param path 缓存目录
     */
    @SubCommand
    fun ConsoleCommandSender.path(path: String) {
        runCatching {
            if (File(path).exists().not()) File(PixivHelperSettings.cachePath).renameTo(File(path))
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