package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.RankMode
import xyz.cssxsh.pixiv.api.app.*
import xyz.cssxsh.pixiv.data.app.IllustInfo
import xyz.cssxsh.pixiv.data.app.UserDetail
import xyz.cssxsh.pixiv.tool.downloadImageUrl
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.BufferedOutputStream
import java.nio.file.attribute.FileTime
import java.util.zip.Deflater.BEST_COMPRESSION

@Suppress("unused")
object PixivCacheCommand : CompositeCommand(
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
        set(value) {
            PixivHelperSettings.delayTime = value
        }

    private var cacheJob: Job? = null

    private suspend fun PixivHelper.getRank(date: String? = null, modes: Array<RankMode> = RankMode.values()) = buildList {
        modes.map { mode ->
            runCatching {
                illustRanking(date = date, mode = mode).illusts
            }.onSuccess {
                add(PixivCacheData.update(it).values)
                logger.verbose("加载排行榜[${mode}]{${it.size}}成功")
            }.onFailure {
                logger.verbose("加载排行榜[${mode}]失败", it)
            }
        }
    }

    private suspend fun PixivHelper.getUserPreviews(uid: Long, limit: Long = 10_000) = buildList {
        (0 until limit step AppApi.PAGE_SIZE).forEach { offset ->
            runCatching {
                userFollowing(uid = uid, offset = offset).userPreviews.flatMap { it.illusts }
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                add(PixivCacheData.update(it).values)
                logger.verbose("加载关注用户作品预览第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.verbose("加载关注用户作品预览第${offset / 30}页失败", it)
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
                logger.verbose("加载关注用户作品时间线第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.verbose("加载关注用户作品时间线第${offset / 30}页失败", it)
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
                logger.verbose("加载推荐用户预览第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.verbose("加载推荐用户预览第${offset / 30}页失败", it)
            }
        }
    }

    private suspend fun CommandSenderOnMessage<MessageEvent>.doCache(
        timeMillis: Long = delayTime,
        block: suspend PixivHelper.() -> List<IllustInfo>
    ) = getHelper().runCatching {
        check(cacheJob?.isActive != true) { "正在缓存中, ${cacheJob}..." }
        launch {
            runCatching {
                PixivCacheData.update(block()).values.also { list ->
                    list.writeToCache()
                    logger.verbose("共 ${list.size} 个作品信息将会被尝试添加")
                }.run {
                    size to count { illust: IllustInfo ->
                        isActive && illust.pid !in PixivCacheData && runCatching {
                            getImages(illust)
                        }.onSuccess {
                            delay(timeMillis)
                        }.onFailure {
                            logger.verbose("获取作品(${illust.pid})[${illust.title}]错误", it)
                        }.isSuccess
                    }
                }
            }.onSuccess { (total, success) ->
                quoteReply("缓存完毕共${total}个新作品, 缓存成功${success}个")
            }.onFailure {
                quoteReply("缓存失败, ${it.message}")
            }
        }.also {
            cacheJob = it
        }
    }.onSuccess {
        quoteReply("添加任务完成${it}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 缓存关注列表
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.follow() = doCache {
        (getFollow() + getUserPreviews(getAuthInfo().user.uid)).flatten()
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
     * 缓存指定用户关注的用户的预览作品
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.preview(uid: Long) = doCache {
        getUserPreviews(uid).flatten().filter { it.isEro() }
    }

    /**
     * 从用户详情加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.user(uid: Long) = doCache {
        val detail: UserDetail = userDetail(uid)
        logger.verbose("用户(${detail.user.id})[${detail.user.name}], 共有${detail.profile.totalIllusts} 个作品")

        (0 .. detail.profile.totalIllusts step AppApi.PAGE_SIZE).mapNotNull { offset ->
            runCatching {
                userIllusts(uid = uid, offset = offset).illusts
            }.onSuccess {
                logger.verbose("加载用户作品第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.verbose("加载用户作品第${offset / 30}页失败", it)
            }.getOrNull()
        }.flatten()
    }

    /**
     * 从文件夹中加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.load() = getHelper().runCatching {
        check(cacheJob?.isActive != true) { "正在缓存中, ${cacheJob}..." }
        launch {
            runCatching {
                PixivHelperSettings.cacheFolder.also {
                    logger.verbose("从 ${it.absolutePath} 加载作品信息")
                }.walk().mapNotNull { file ->
                    if (file.isDirectory && file.name.matches("""^[0-9]+$""".toRegex())) {
                        file.name.toLong()
                    } else {
                        null
                    }
                }.toSet().let { list ->
                    list - PixivCacheData.caches().keys
                }.also { list ->
                    logger.verbose("共 ${list.size} 个作品信息将会被尝试添加")
                }.run {
                    size to count { pid ->
                        isActive && runCatching {
                            getImages(getIllustInfo(pid))
                        }.onFailure {
                            logger.verbose("获取作品(${pid})错误", it)
                        }.isSuccess
                    }
                }
            }.onSuccess { (total, success) ->
                quoteReply("缓存完毕共${total}个新作品, 缓存成功${success}个")
            }.onFailure {
                logger.warning("缓存失败", it)
                quoteReply("缓存失败, ${it.message}")
            }
        }.also {
            cacheJob = it
        }
    }.onSuccess {
        quoteReply("添加任务完成${it}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess


    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.flush() = getHelper().runCatching {
        check(cacheJob?.isActive != true) { "正在缓存中, ${cacheJob}..." }
        launch {
            runCatching {
                PixivCacheData.caches().keys.count { pid ->
                    runCatching {
                        getIllustInfo(pid, true)
                    }.onFailure {
                        logger.warning("刷新")
                    }.isFailure
                }
            }.onSuccess {
                quoteReply("缓存完毕, 共${it}个缓存失败")
            }.onFailure {
                quoteReply("缓存失败, ${it.message}")
            }
        }.also {
            cacheJob = it
        }
    }.onSuccess {
        quoteReply("添加任务完成${it}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 强制停止缓存
     */
    @SubCommand("cancel", "stop")
    suspend fun CommandSenderOnMessage<MessageEvent>.cancel() = runCatching {
        cacheJob?.cancelAndJoin()
    }.onSuccess {
        quoteReply("任务${cacheJob}已停止")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 检查当前缓存中不可读，删除并重新下载
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.check() = getHelper().runCatching {
        PixivCacheData.caches().values.also {
            logger.verbose("共有 ${it.size} 个作品需要检查")
        }.count { info ->
            runCatching {
                val dir = PixivHelperSettings.imagesFolder(info.pid)
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
    }.onSuccess {
        quoteReply("检查缓存完毕，无法修复错误数: $it")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 色图之王
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.king() = getHelper().runCatching {
        (PixivCacheData.eros() + PixivCacheData.r18s()).map {
            getIllustInfo(it.pid, true)
        }.maxByOrNull {
            it.totalBookmarks ?: 0
        }.let {
            buildMessage(requireNotNull(it) { "缓存为空" })
        }
    }.onSuccess { lists ->
        lists.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    @Serializable
    data class TagData(val tags: Map<String, Int>)
    /**
     * 标签统计
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.tags() = getHelper().runCatching {
        val json = Json {
            prettyPrint = true
            isLenient = true
            allowStructuredMapKeys = true
        }
        buildMap<String, Int> {
            PixivCacheData.eros().flatMap {
                it.tags
            }.forEach { tag ->
                tag.name.let {
                    put(it, getOrDefault(it, 0) + 1)
                }
                tag.translatedName?.let {
                    put(it, getOrDefault(it, 0) + 1)
                }
            }
        }.let {
            logger.info("共有tag: ${it.size}")
            json.encodeToString(TagData.serializer(), TagData(it))
        }
    }.onSuccess { text ->
        File(PixivHelperSettings.cachePath, "tags.json").apply {
            writeText(text)
        }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess


    @SubCommand
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun ConsoleCommandSender.tozip(uid: Long, path: String) = withContext(Dispatchers.IO) {
        ZipOutputStream(File(path).apply { createNewFile() }.outputStream()).use { zipOutputStream ->
            BufferedOutputStream(zipOutputStream).use { buffer ->
                zipOutputStream.setLevel(BEST_COMPRESSION)
                PixivCacheData.caches().values.filter {
                    it.uid == uid
                }.also {
                    logger.verbose("共${it.size} 个作品将写入文件")
                }.forEach { info ->
                    PixivHelperSettings.imagesFolder(info.pid).listFiles()?.forEach { file ->
                        zipOutputStream.putNextEntry(ZipEntry("[${info.pid}](${info.title})/${file.name}").apply {
                            creationTime = FileTime.fromMillis(info.createDate.utc.unixMillisLong)
                            lastModifiedTime = FileTime.fromMillis(info.createDate.utc.unixMillisLong)
                        })
                        buffer.write(file.readBytes())
                    }
                }
            }
            logger.verbose("${uid}压缩完毕！")
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
            logger.info("色图作品(${it.pid})[${it.title}]信息将从缓存移除, 目前共${PixivCacheData.caches().size}条缓存")
        }
    }
}