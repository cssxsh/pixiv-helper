package xyz.cssxsh.mirai.plugin.command

import com.soywiz.klock.wrapped.WDateTimeSpan
import com.soywiz.klock.wrapped.WDateTimeTz
import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.delayTime
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.mirai.plugin.tools.PanUpdater.update
import xyz.cssxsh.mirai.plugin.tools.PoiTool.saveCacheToXlsxAsync
import xyz.cssxsh.pixiv.RankMode
import xyz.cssxsh.pixiv.api.app.*
import xyz.cssxsh.pixiv.data.app.IllustInfo
import xyz.cssxsh.pixiv.tool.downloadImageUrl
import java.io.File

@Suppress("unused")
object PixivCacheCommand : CompositeCommand(
    PixivHelperPlugin,
    "cache",
    description = "缓存指令",
    prefixOptional = true
), PixivHelperLogger {

    private var compressJob:  Deferred<File>? = null

    private var panJob: Job? = null

    private var backupJob:  Deferred<List<File>>? = null

    private var saveJob:  Deferred<File>? = null

    private suspend fun PixivHelper.getRank(date: String? = null, modes: Array<RankMode> = RankMode.values()) =
        buildList {
            modes.map { mode ->
                runCatching {
                    illustRanking(date = date, mode = mode).illusts
                }.onSuccess {
                    add(PixivCacheData.update(it).values)
                    logger.verbose("加载排行榜[${mode}]{${it.size}}成功")
                }.onFailure {
                    logger.warning("加载排行榜[${mode}]失败", it)
                }
            }
        }

    private suspend fun PixivHelper.getUserFollowingPreviews(uid: Long, limit: Long = 10_000) = buildList {
        (0 until limit step AppApi.PAGE_SIZE).forEach { offset ->
            runCatching {
                userFollowing(uid = uid, offset = offset).userPreviews.flatMap { it.illusts }
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                add(PixivCacheData.update(it).values)
                logger.verbose("加载用户(${uid})关注用户作品预览第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.warning("加载用户(${uid})关注用户作品预览第${offset / 30}页失败", it)
            }
        }
    }

    private suspend fun PixivHelper.getFollow(limit: Long = 10_000) = buildList {
        (0 until limit step AppApi.PAGE_SIZE).forEach { offset ->
            runCatching {
                illustFollow(offset = offset).illusts
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                add(PixivCacheData.update(it).values)
                logger.verbose("加载用户(${getAuthInfo().user.uid})关注用户作品时间线第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.warning("加载用户(${getAuthInfo().user.uid})关注用户作品时间线第${offset / 30}页失败", it)
            }
        }
    }

    private suspend fun PixivHelper.getRecommended(limit: Long = 10_000) = buildList {
        (0 until limit step AppApi.PAGE_SIZE).forEach { offset ->
            runCatching {
                userRecommended(offset = offset).userPreviews.flatMap { it.illusts }
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                add(PixivCacheData.update(it).values)
                logger.verbose("加载用户(${getAuthInfo().user.uid})推荐用户预览第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.warning("加载用户(${getAuthInfo().user.uid})推荐用户预览第${offset / 30}页失败", it)
            }
        }
    }

    private suspend fun PixivHelper.getBookmarks(uid: Long, limit: Long = 10_000) = buildList {
        var url = AppApi.USER_BOOKMARKS_ILLUST
        (0 until limit step AppApi.PAGE_SIZE).forEach { _ ->
            runCatching {
                userBookmarksIllust(uid = uid, url = url)
            }.onSuccess { (list, nextUrl) ->
                if (nextUrl == null) return@buildList
                add(PixivCacheData.update(list).values)
                logger.verbose("加载用户(${uid})收藏页{${list.size}} ${url}成功")
                url = nextUrl
            }.onFailure {
                logger.warning("加载用户(${uid})收藏页${url}失败", it)
            }
        }
    }

    private suspend fun CommandSenderOnMessage<MessageEvent>.doCache(
        block: suspend PixivHelper.() -> List<IllustInfo>
    ) = getHelper().runCatching {
        check(cacheJob?.isActive != true) { "正在缓存中, ${cacheJob}..." }
        launch(Dispatchers.IO) {
            PixivCacheData.update(block()).values.apply {
                writeToCache()
                logger.verbose("共${size}个作品信息将会被尝试添加")
            }.sortedBy {
                it.pid
            }.also {
                it.runCatching {
                    reply("{${first().pid}...${last().pid}}共${size}个新作品等待缓存")
                }
            }.runCatching {
                size to count { illust: IllustInfo ->
                    isActive && illust.pid !in PixivCacheData && runCatching {
                        getImages(illust)
                    }.onSuccess {
                        delay(delayTime)
                    }.onFailure {
                        logger.warning("获取作品(${illust.pid})[${illust.title}]错误", it)
                    }.isSuccess
                }
            }.onSuccess { (total, success) ->
                reply("缓存完毕共${total}个新作品, 缓存成功${success}个")
            }.onFailure {
                reply("缓存失败, ${it.message}")
            }
        }.also {
            cacheJob = it
        }
    }.onSuccess { job ->
        quoteReply("新作品等待缓存, 添加任务完成${job}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 缓存关注列表
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.follow() = doCache {
        (getFollow() + getUserFollowingPreviews(getAuthInfo().user.uid)).flatten()
    }

    /**
     *
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.rank() = doCache {
        getRank().flatten()
    }

    /**
     *
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.recommended() = doCache {
        getRecommended().flatten().filter { it.isEro() }
    }

    /**
     *
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.bookmarks(uid: Long) = doCache {
        getBookmarks(uid).flatten()
    }

    /**
     * 缓存指定用户关注的用户的预览作品
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.preview(uid: Long) = doCache {
        getUserFollowingPreviews(uid).flatten().filter { it.isEro() }
    }

    private suspend fun PixivHelper.getUserIllusts(uid: Long) = userDetail(uid).let { detail ->
        logger.verbose("用户(${detail.user.id})[${detail.user.name}], 共有${detail.profile.totalIllusts}个作品")

        (0 until detail.profile.totalIllusts step AppApi.PAGE_SIZE).mapNotNull { offset ->
            runCatching {
                userIllusts(uid = uid, offset = offset).illusts
            }.onSuccess {
                logger.verbose("加载用户(${uid})作品第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.warning("加载用户(${uid})作品第${offset / 30}页失败", it)
            }.getOrNull()
        }.flatten()
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.alias() = getHelper().runCatching {
        check(cacheJob?.isActive != true) { "正在缓存中, ${cacheJob}..." }
        launch(Dispatchers.IO) {
            PixivAliasData.aliases.values.toSet().sorted().also {
                logger.verbose("{${it.first()}...${it.last()}}共${it.size}个画师需要缓存")
                reply("列表中共${it.size}个画师需要缓存")
            }.forEach { uid ->
                runCatching {
                    PixivCacheData.update(getUserIllusts(uid)).values.sortedBy {
                        it.pid
                    }.apply {
                        writeToCache()
                        logger.verbose("用户(${uid}), 共有{${first().pid}...${last().pid}}共${size}个作品需要缓存")
                    }.runCatching {
                        size to count { illust ->
                            isActive && illust.pid !in PixivCacheData && runCatching {
                                getImages(illust)
                            }.onSuccess {
                                delay(delayTime)
                            }.onFailure {
                                logger.warning("获取作品(${illust.pid})[${illust.title}]错误", it)
                            }.isSuccess
                        }
                    }.onSuccess { (total, success) ->
                        reply("用户($uid)缓存完毕共${total}个新作品, 缓存成功${success}个")
                    }.onFailure {
                        reply("用户($uid)缓存失败, ${it.message}")
                    }
                }
            }
        }.also {
            cacheJob = it
        }
    }.onSuccess { job ->
        quoteReply("从别名列表加载作品, 添加任务完成${job}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
    /**
     * 从用户详情加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.user(uid: Long) = doCache {
        getUserIllusts(uid)
    }

    /**
     * 从文件夹中加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.load() = getHelper().runCatching {
        check(cacheJob?.isActive != true) { "正在缓存中, ${cacheJob}..." }
        launch(Dispatchers.IO) {
            PixivHelperSettings.cacheFolder.also {
                logger.verbose("从 ${it.absolutePath} 加载作品信息")
            }.walk().maxDepth(3).mapNotNull { file ->
                if (file.isDirectory && file.name.matches("""^[0-9]+$""".toRegex())) {
                    file.name.toLong()
                } else {
                    null
                }
            }.toSet().let { list ->
                list - PixivCacheData.caches().keys
            }.sorted().also {
                it.runCatching {
                    reply("{${first()}...${last()}}共${size}个作品信息将会被尝试添加")
                }
            }.runCatching {
                size to count { pid ->
                    isActive && pid !in PixivCacheData && runCatching {
                        getImages(getIllustInfo(pid))
                    }.onFailure {
                        logger.warning("获取作品(${pid})错误", it)
                    }.isSuccess
                }
            }.onSuccess { (total, success) ->
                reply("缓存完毕共${total}个新作品, 缓存成功${success}个")
            }.onFailure {
                logger.warning("缓存失败", it)
                reply("缓存失败, ${it.message}")
            }
        }.also {
            cacheJob = it
        }
    }.onSuccess { job ->
        quoteReply("从缓存加载作品, 添加任务完成${job}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess


    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.flush() = getHelper().runCatching {
        check(cacheJob?.isActive != true) { "正在缓存中, ${cacheJob}..." }
        launch(Dispatchers.IO) {
            PixivCacheData.caches().values.filter {
                (WDateTimeTz.nowLocal() - it.createDate) < WDateTimeSpan(weeks = 1).timeSpan
            }.also {
                logger.verbose("{${it.first()}...${it.last()}}共有${it.size}个作品需要刷新")
            }.runCatching {
                size to count { info ->
                    runCatching {
                        getIllustInfo(info.pid, true)
                    }.onSuccess {
                        logger.verbose("(${info.pid})<${info.getCreateDateText()}>[${info.title}]刷新成功")
                    }.onFailure {
                        logger.warning("(${info.pid})<${info.getCreateDateText()}>[${info.title}]刷新失败", it)
                    }.isSuccess
                }
            }.onSuccess { (total, success) ->
                reply("缓存完毕需要更新${total}个作品, 缓存成功${success}个")
            }.onFailure {
                logger.warning("缓存失败", it)
                reply("缓存失败, ${it.message}")
            }
        }.also {
            cacheJob = it
        }
    }.onSuccess { job ->
        quoteReply("作品信息等待缓存, 添加任务完成${job}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 强制停止缓存
     */
    @SubCommand("cancel", "stop")
    suspend fun CommandSenderOnMessage<MessageEvent>.cancel() = getHelper().runCatching {
        cacheJob?.apply {
            cancelAndJoin()
        }
    }.onSuccess {
        quoteReply("任务${it}已停止")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 检查当前缓存中不可读，删除并重新下载
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.check() = getHelper().runCatching {
        PixivCacheData.caches().values.sortedBy {
            it.pid
        }.also {
            logger.verbose("{${it.first().pid}...${it.last().pid}}共有 ${it.size} 个作品需要检查")
        }.run {
            size to count { info ->
                runCatching {
                    val dir = PixivHelperSettings.imagesFolder(info.pid)
                    File(dir, "${info.pid}.json").run {
                        if (canRead().not()) {
                            logger.warning("$absolutePath 不可读， 文件将删除重新下载，删除结果：${delete()}")
                            illustDetail(info.pid).illust.writeTo(this)
                        }
                    }
                    info.originUrl.filter { url ->
                        File(dir, url.getFilename()).canRead().not()
                    }.let { urls ->
                        downloadImageUrl<ByteArray, Unit>(urls) { _, url, result ->
                            File(dir, url.getFilename()).run {
                                result.onSuccess {
                                    logger.warning("$absolutePath 不可读， 文件将删除重新下载，删除结果：${delete()}")
                                    writeBytes(it)
                                }.onFailure {
                                    logger.warning("$url 下载失败", it)
                                }
                            }
                        }
                    }
                }.onFailure {
                    logger.warning("作品(${info.pid})[${info.title}]缓存出错", it)
                }.isFailure
            }
        }
    }.onSuccess { (size, num) ->
        quoteReply("检查缓存完毕，总计${size}, 无法修复数: $num")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 统计
     */
    @SubCommand
    fun ConsoleCommandSender.xlsx() {
        check(saveJob?.isActive != true) { "正在压缩中, ${saveJob}..." }
        saveJob = saveCacheToXlsxAsync()
    }


    @SubCommand
    fun ConsoleCommandSender.tozip(uid: Long) {
        check(compressJob?.isActive != true) { "正在压缩中, ${compressJob}..." }
        PixivCacheData.caches().values.filter {
            it.uid == uid
        }.let {
            compressJob = Zipper.compressAsync(it, "user")
        }
    }

    @SubCommand
    fun ConsoleCommandSender.backup() {
        check(backupJob?.isActive != true) { "正在压缩中, ${backupJob}..." }
        backupJob = Zipper.backupAsync()
    }

    @SubCommand
    fun ConsoleCommandSender.pan(file: String) {
        check(panJob?.isActive != true) { "正在上传中, ${panJob}..." }
        PixivHelperPlugin.update(file, file, PixivHelperSettings.panConfig) { data, (count, size) ->
            logger.verbose("MD5将记录 ${count}/${size}  $data")
        }.also {
            panJob = it
        }
    }

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
        logger.info("delay: $delayTime -> $timeMillis")
        delayTime = timeMillis
    }

    /**
     * 设置缓存延迟时间
     */
    @SubCommand
    fun ConsoleCommandSender.remove(pid: Long) {
        PixivCacheData.remove(pid)?.let {
            logger.info("色图作品(${it.pid})[${it.title}]信息将从缓存移除")
        }
        PixivHelperSettings.imagesFolder(pid).apply {
            listFiles()?.forEach {
                it.delete()
            }
            logger.info("色图作品(${pid})文件夹将删除，结果${delete()}")
        }
    }

    @SubCommand
    fun ConsoleCommandSender.delete(uid: Long) {
        PixivCacheData.caches().values.filter {
            it.uid == uid
        }.also {
            logger.verbose("USER(${uid})共${it.size}个作品需要删除")
        }.forEach {
            PixivCacheData.remove(it.pid)
            logger.info("色图作品(${it.pid})[${it.title}]信息将从缓存移除")
            PixivHelperSettings.imagesFolder(it.pid).apply {
                listFiles()?.forEach { file ->
                    file.delete()
                }
                logger.info("色图作品(${it.pid})[${it.title}]文件夹将删除，结果${delete()}")
            }
        }
    }
}