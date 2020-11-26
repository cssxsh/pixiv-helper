package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.verbose
import net.mamoe.mirai.utils.warning
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.delayTime
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.mirai.plugin.tools.PanUpdater.update
import xyz.cssxsh.pixiv.RankMode
import xyz.cssxsh.pixiv.WorkContentType
import xyz.cssxsh.pixiv.api.app.*
import xyz.cssxsh.pixiv.data.app.UserDetail
import java.io.File

@Suppress("unused")
object PixivCacheCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "cache",
    description = "缓存指令"
), PixivHelperLogger {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private var panJob: Job? = null

    private suspend fun PixivHelper.getRank(modes: List<RankMode> = RankMode.values().asList()) =
        modes.mapNotNull { mode ->
            runCatching {
                illustRanking(mode = mode).illusts
            }.onSuccess {
                logger.verbose { "加载排行榜[${mode}]{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载排行榜[${mode}]失败" }, it)
            }.getOrNull()
        }.flatten()

    private suspend fun PixivHelper.getFollowIllusts(limit: Long = 10_000) = buildList {
        (0 until limit step AppApi.PAGE_SIZE).forEach { offset ->
            runCatching {
                illustFollow(offset = offset).illusts
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                add(PixivCacheData.update(it).values)
                logger.verbose { "加载用户(${getAuthInfo().user.uid})关注用户作品时间线第${offset / 30}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载用户(${getAuthInfo().user.uid})关注用户作品时间线第${offset / 30}页失败" }, it)
            }
        }
    }.flatten()

    private suspend fun PixivHelper.getRecommended(limit: Long = 10_000) = buildList {
        (0 until limit step AppApi.PAGE_SIZE).forEach { offset ->
            runCatching {
                userRecommended(offset = offset).userPreviews.flatMap { it.illusts }
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                add(PixivCacheData.update(it).values)
                logger.verbose { "加载用户(${getAuthInfo().user.uid})推荐用户预览第${offset / 30}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载用户(${getAuthInfo().user.uid})推荐用户预览第${offset / 30}页失败" }, it)
            }
        }
    }.flatten()

    private suspend fun PixivHelper.getBookmarks(uid: Long, limit: Long = 10_000) = buildList {
        var url = AppApi.USER_BOOKMARKS_ILLUST
        (0 until limit step AppApi.PAGE_SIZE).forEach { _ ->
            runCatching {
                userBookmarksIllust(uid = uid, url = url)
            }.onSuccess { (list, nextUrl) ->
                if (nextUrl == null) return@buildList
                add(PixivCacheData.update(list).values)
                logger.verbose { "加载用户(${uid})收藏页{${list.size}} ${url}成功" }
                url = nextUrl
            }.onFailure {
                logger.warning({ "加载用户(${uid})收藏页${url}失败" }, it)
            }
        }
    }.flatten()

    private suspend fun PixivHelper.getUserIllusts(detail: UserDetail) =
        (0 until detail.profile.totalIllusts step AppApi.PAGE_SIZE).mapNotNull { offset ->
            runCatching {
                userIllusts(uid = detail.user.id, offset = offset).illusts
            }.onSuccess {
                logger.verbose { "加载用户(${detail.user.id})作品第${offset / 30}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载用户(${detail.user.id})作品第${offset / 30}页失败" }, it)
            }.getOrNull()
        }.flatten()

    private suspend fun PixivHelper.getUserFollowing(detail: UserDetail) =
        (0 until detail.profile.totalFollowUsers step AppApi.PAGE_SIZE).mapNotNull { offset ->
            runCatching {
                userFollowing(uid = detail.user.id, offset = offset).userPreviews.map { it.user }
            }.onSuccess {
                logger.verbose { "加载用户(${detail.user.id})关注用户第${offset / 30}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载用户(${detail.user.id})关注用户第${offset / 30}页失败" }, it)
            }.getOrNull()
        }.flatten()

    /**
     * 缓存关注列表
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.follow() =
        getHelper().addCacheJob("FOLLOW") { getFollowIllusts() }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.rank() =
        getHelper().addCacheJob("RANK") { getRank() }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.recommended() =
        getHelper().addCacheJob("RECOMMENDED") { getRecommended().filter { it.isEro() } }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.bookmarks(uid: Long) =
        getHelper().addCacheJob("BOOKMARKS") { getBookmarks(uid) }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.alias() = getHelper().runCatching {
        PixivAliasData.aliases.values.toSet().sorted().also {
            logger.verbose { "别名中{${it.first()}...${it.last()}}共${it.size}个画师需要缓存" }
            reply("别名列表中共${it.size}个画师需要缓存")
        }.count { uid ->
            runCatching {
                userDetail(uid).let { detail ->
                    logger.verbose { "USER(${uid})有${detail.profile.totalIllusts}个作品" }
                    if (detail.profile.totalIllusts > PixivCacheData.caches().count { it.value.uid == uid }) {
                        addCacheJob("USER(${uid})") {
                            getUserIllusts(detail)
                        }
                    } else {
                        false
                    }
                }
            }.onFailure {
                logger.warning({ "别名缓存${uid}失败" }, it)
            }.getOrDefault(false)
        }
    }.onSuccess { num ->
        quoteReply("别名列表加载作品, 添加任务共${num}个")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.following() = getHelper().runCatching {
        getUserFollowing(userDetail(getAuthInfo().user.uid)).sortedBy { it.id }.also {
            logger.verbose { "关注中{${it.first().id}...${it.last().id}}共${it.size}个画师需要缓存" }
            reply("关注列表中共${it.size}个画师需要缓存")
        }.count { user ->
            runCatching {
                userDetail(user.id).let { detail ->
                    logger.verbose { "USER(${user.id})有${detail.profile.totalIllusts}个作品" }
                    if (detail.profile.totalIllusts > PixivCacheData.caches().count { it.value.uid == user.id }) {
                        addCacheJob("USER(${user.id})") {
                            getUserIllusts(detail)
                        }
                    } else {
                        false
                    }
                }
            }.onFailure {
                logger.warning({ "关注缓存${user.id}失败" }, it)
            }.getOrDefault(false)
        }
    }.onSuccess { num ->
        quoteReply("关注列表加载作品, 添加任务共${num}个")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 从用户详情加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.user(uid: Long) =
        getHelper().addCacheJob("USER(${uid})") { getUserIllusts(userDetail(uid)) }

    /**
     * 从文件夹中加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.load() =
        getHelper().addCacheJob("LOAD") {
            PixivHelperSettings.cacheFolder.also {
                logger.verbose { "从 ${it.absolutePath} 加载作品信息" }
            }.walk().maxDepth(3).mapNotNull { file ->
                if (file.isDirectory && file.name.matches("""^[0-9]+$""".toRegex())) {
                    file.name.toLong()
                } else {
                    null
                }
            }.toSet().filter {
                it !in PixivCacheData
            }.map { getIllustInfo(it) }
        }

    /**
     * 强制停止缓存
     */
    @SubCommand("cancel", "stop")
    suspend fun CommandSenderOnMessage<MessageEvent>.cancel() = getHelper().runCatching {
        cacheStop()
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
        PixivCacheData.toMap().values.sortedBy {
            it.pid
        }.also {
            logger.verbose { "{${it.first().pid}...${it.last().pid}}共有 ${it.size} 个作品需要检查" }
        }.run {
            size to count { info ->
                runCatching {
                    val dir = PixivHelperSettings.imagesFolder(info.pid)
                    File(dir, "${info.pid}.json").run {
                        if (canRead().not()) {
                            logger.warning { "$absolutePath 不可读， 文件将删除重新下载，删除结果：${delete()}" }
                            illustDetail(info.pid).illust.writeTo(this)
                        }
                    }
                    info.originUrl.filter { url ->
                        File(dir, url.getFilename()).canRead().not()
                    }.let { urls ->
                        downloadImageUrls(urls = urls, dir = dir).forEachIndexed { index, result ->
                            result.onFailure {
                                logger.warning({ "[${urls[index]}]修复出错" }, it)
                            }.onSuccess {
                                logger.info { "[${urls[index]}]修复成功" }
                            }
                        }
                    }
                }.onFailure {
                    logger.warning({ "作品(${info.pid})[${info.title}]修复出错" }, it)
                    reply("作品(${info.pid})[${info.title}]修复出错, ${it.message}")
                }.isFailure
            }
        }
    }.onSuccess { (size, num) ->
        quoteReply("检查缓存完毕，总计${size}, 无法修复数: $num")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    @SubCommand
    fun ConsoleCommandSender.pan(file: String) {
        check(panJob?.isActive != true) { "正在上传中, ${panJob}..." }
        PixivHelperPlugin.update(file, file, PixivHelperSettings.panConfig) { data, count, size ->
            logger.verbose { "MD5将记录 ${count}/${size} $data" }
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
        logger.info { "delay: $delayTime -> $timeMillis" }
        delayTime = timeMillis
    }

    /**
     * 设置缓存延迟时间
     */
    @SubCommand
    fun ConsoleCommandSender.remove(pid: Long) {
        PixivCacheData.remove(pid)?.let {
            logger.info { "色图作品(${it.pid})[${it.title}]信息将从缓存移除" }
        }
        PixivHelperSettings.imagesFolder(pid).apply {
            listFiles()?.forEach {
                it.delete()
            }
            logger.info { "色图作品(${pid})文件夹将删除，结果${delete()}" }
        }
    }

    @SubCommand
    fun ConsoleCommandSender.delete(uid: Long) {
        PixivCacheData.filter { (_, illust) ->
            illust.uid == uid
        }.values.also {
            logger.verbose { "USER(${uid})共${it.size}个作品需要删除" }
        }.forEach {
            PixivCacheData.remove(it.pid)
            logger.info { "色图作品(${it.pid})[${it.title}]信息将从缓存移除" }
            PixivHelperSettings.imagesFolder(it.pid).apply {
                listFiles()?.forEach { file ->
                    file.delete()
                }
                logger.info { "色图作品(${it.pid})[${it.title}]文件夹将删除，结果${delete()}" }
            }
        }
    }

    @SubCommand
    fun ConsoleCommandSender.nomanga() {
        PixivCacheData.filter { (_, illust) ->
            illust.type == WorkContentType.MANGA
        }.values.also {
            logger.verbose { "共${it.size}个漫画作品需要删除" }
        }.forEach {
            PixivCacheData.remove(it.pid)
            logger.info { "色图作品(${it.pid})[${it.title}]信息将从缓存移除" }
            PixivHelperSettings.imagesFolder(it.pid).apply {
                listFiles()?.forEach { file ->
                    file.delete()
                }
                logger.info { "色图作品(${it.pid})[${it.title}]文件夹将删除，结果${delete()}" }
            }
        }
    }
}