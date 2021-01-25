package xyz.cssxsh.mirai.plugin.command

import io.ktor.client.features.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.command.descriptor.CommandValueArgumentParser
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.command.descriptor.buildCommandArgumentContext
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperDownloader.downloadImageUrls
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.mirai.plugin.tools.PanUpdater.update
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.RankMode
import xyz.cssxsh.pixiv.api.apps.*
import xyz.cssxsh.pixiv.data.apps.UserDetail
import xyz.cssxsh.pixiv.data.apps.UserPreview
import java.io.File
import java.time.LocalDate
import java.time.Year

@Suppress("unused")
object PixivCacheCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "cache",
    description = "PIXIV缓存指令",
    overrideContext = buildCommandArgumentContext {
        LocalDate::class with object : CommandValueArgumentParser<LocalDate> {
            override fun parse(raw: String, sender: CommandSender): LocalDate =
                LocalDate.parse(raw)
        }
        Year::class with object : CommandValueArgumentParser<Year> {
            override fun parse(raw: String, sender: CommandSender): Year =
                Year.parse(raw)
        }
    }
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private var panJob: Job? = null

    private fun UserPreview.isLoaded(): Boolean = useArtWorkInfoMapper { mapper ->
        illusts.all { mapper.contains(it.pid) }
    }

    private fun UserDetail.count() = useArtWorkInfoMapper { mapper ->
        mapper.countByUid(user.id)
    }

    private fun UserDetail.total() = profile.totalIllusts + profile.totalManga

    // FIXME: 交给插件设置加载
    private val cacheRanks = listOf(
        RankMode.MONTH,
        RankMode.WEEK,
        RankMode.WEEK_ORIGINAL,
        RankMode.DAY,
        RankMode.DAY_MALE,
        RankMode.DAY_FEMALE,
    )

    private suspend fun PixivHelper.getRank(mode: RankMode, date: LocalDate?, limit: Long = 500) = buildList {
        (0 until limit step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
            if (isActive) runCatching {
                illustRanking(mode = mode, date = date, offset = offset, ignore = apiIgnore).illusts
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                addAll(it)
                logger.verbose { "加载排行榜[${mode}](${date ?: "new"})第${page}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载排行榜[${mode}](${date ?: "new"})第${page}页失败" }, it)
            }
        }
    }

    private fun loadDayOfYears(year: Year) = buildList {
        (1..12).forEach { month ->
            (0..3L).forEach { week ->
                add(year.atMonth(month).atEndOfMonth().plusWeeks(week))
            }
        }
    }

    private suspend fun PixivHelper.getFollowIllusts(limit: Long = 10_000) = buildList {
        (0 until limit step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
            if (isActive) runCatching {
                illustFollow(offset = offset, ignore = apiIgnore).illusts
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                addAll(it)
                logger.verbose { "加载用户(${getAuthInfo().user.uid})关注用户作品时间线第${page}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载用户(${getAuthInfo().user.uid})关注用户作品时间线第${page}页失败" }, it)
            }
        }
    }

    private suspend fun PixivHelper.getRecommended(limit: Long = 10_000) = buildList {
        (0 until limit step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
            if (isActive) runCatching {
                userRecommended(offset = offset, ignore = apiIgnore).userPreviews.flatMap { it.illusts }
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                addAll(it)
                logger.verbose { "加载用户(${getAuthInfo().user.uid})推荐用户预览第${page}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载用户(${getAuthInfo().user.uid})推荐用户预览第${page}页失败" }, it)
            }
        }
    }

    private suspend fun PixivHelper.getBookmarks(uid: Long, limit: Long = 10_000) = buildList {
        (0 until limit step AppApi.PAGE_SIZE).fold<Long, String?>(AppApi.USER_BOOKMARKS_ILLUST) { url, _ ->
            if (isActive && url != null) {
                runCatching {
                    userBookmarksIllust(uid = uid, url = url, ignore = apiIgnore)
                }.onSuccess { (list, nextUrl) ->
                    if (nextUrl == null) return@buildList
                    addAll(list)
                    logger.verbose { "加载用户(${uid})收藏页{${list.size}} ${url}成功" }
                }.onFailure {
                    logger.warning({ "加载用户(${uid})收藏页${url}失败" }, it)
                }.getOrNull()?.nextUrl
            } else {
                null
            }
        }
    }

    private suspend fun PixivHelper.getUserIllusts(detail: UserDetail) = buildList {
        (0 until detail.total() step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
            if (isActive) runCatching {
                userIllusts(uid = detail.user.id, offset = offset, ignore = apiIgnore).illusts
            }.onSuccess {
                addAll(it)
                logger.verbose { "加载用户(${detail.user.id})作品第${page}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载用户(${detail.user.id})作品第${page}页失败" }, it)
            }
        }
    }

    private suspend fun PixivHelper.getUserIllusts(uid: Long) =
        getUserIllusts(userDetail(uid = uid, ignore = apiIgnore))

    private suspend fun PixivHelper.getUserFollowingPreview(detail: UserDetail) = buildList {
        (0 until detail.profile.totalFollowUsers step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
            if (isActive) runCatching {
                userFollowing(uid = detail.user.id, offset = offset, ignore = apiIgnore).userPreviews
            }.onSuccess {
                addAll(it)
                logger.verbose { "加载用户(${detail.user.id})关注用户第${page}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载用户(${detail.user.id})关注用户第${page}页失败" }, it)
            }
        }
    }

    private suspend fun PixivHelper.getUserFollowingPreview() =
        getUserFollowingPreview(userDetail(uid = getAuthInfo().user.uid, ignore = apiIgnore))

    /**
     * 缓存关注列表
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.follow() =
        getHelper().addCacheJob("FOLLOW") { getFollowIllusts() }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.rank(date: LocalDate? = null) = getHelper().run {
        cacheRanks.forEach { mode ->
            addCacheJob("RANK[${mode.name}](${date ?: "new"})") {
                getRank(mode = mode, date = date)
            }
        }
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.year(year: Year) = getHelper().run {
        loadDayOfYears(year).forEach { date ->
            addCacheJob("YEAR[${year}]($date)") {
                getRank(mode = RankMode.MONTH, date = date).filter { it.isEro() }
            }
        }
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.recommended() =
        getHelper().addCacheJob("RECOMMENDED") { getRecommended() }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.bookmarks(uid: Long) =
        getHelper().addCacheJob("BOOKMARKS") { getBookmarks(uid) }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.alias() = getHelper().runCatching {
        PixivAliasData.aliases.values.toSet().sorted().also { list ->
            logger.verbose { "别名中{${list.first()}...${list.last()}}共${list.size}个画师需要缓存" }
            launch {
                list.forEachIndexed { index, uid ->
                    if (isActive) runCatching {
                        userDetail(uid = uid, ignore = apiIgnore).let { detail ->
                            if (detail.total() > detail.count()) {
                                logger.verbose { "${index}.USER(${uid})有${detail.total()}个作品尝试缓存" }
                                addCacheJob("${index}.USER(${uid})") {
                                    getUserIllusts(detail)
                                }
                            }
                        }
                    }.onFailure {
                        logger.warning({ "别名缓存${uid}失败" }, it)
                    }
                }
            }
        }
    }.onSuccess {
        sendMessage("别名列表中共${it.size}个画师需要缓存")
    }.onFailure {
        sendMessage(it.toString())
    }.isSuccess

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.followAll() = getHelper().runCatching {
        getUserFollowingPreview().sortedBy { it.user.id }.also { list ->
            logger.verbose { "关注中{${list.first().user.id}...${list.last().user.id}}共${list.size}个画师需要缓存" }
            launch {
                list.forEachIndexed { index, preview ->
                    if (isActive) runCatching {
                        if (preview.isLoaded().not()) {
                            userDetail(uid = preview.user.id, ignore = apiIgnore).let { detail ->
                                if (detail.total() > detail.count() + preview.illusts.size) {
                                    logger.verbose { "${index}.USER(${detail.user.id})有${detail.total()}个作品尝试缓存" }
                                    addCacheJob("${index}.USER(${detail.user.id})") {
                                        getUserIllusts(detail)
                                    }
                                } else {
                                    logger.verbose { "${index}.USER(${detail.user.id})有${preview.illusts.size}个作品尝试缓存" }
                                    addCacheJob("${index}.USER_PREVIEW(${detail.user.id})") {
                                        preview.illusts
                                    }
                                }
                            }
                        }
                    }.onFailure {
                        logger.warning({ "关注缓存${preview.user.id}失败" }, it)
                    }
                }
            }
        }
    }.onSuccess {
        sendMessage("关注列表中共${it.size}个画师需要缓存")
    }.onFailure {
        sendMessage(it.toString())
    }.isSuccess

    /**
     * 从用户详情加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.user(uid: Long) =
        getHelper().addCacheJob("USER(${uid})") { getUserIllusts(uid = uid) }

    private fun File.listDirs(regex: Regex) =
        listFiles { file -> file.name.matches(regex) && file.isDirectory } ?: emptyArray()

    private fun File.isIgnore(start: Long = 0L) =
        name.replace('_', '9').toLong() < start

    /**
     * 从文件夹中加载信息
     */
    @SubCommand
    fun CommandSenderOnMessage<MessageEvent>.load(start: Long = 0L) = getHelper().run {
        PixivHelperSettings.cacheFolder.also {
            logger.verbose { "从 ${it.absolutePath} 加载作品信息" }
        }.listDirs("""\d{3}______""".toRegex()).forEach { first ->
            if (first.isIgnore(start).not()) {
                addCacheJob("LOAD(${first.name})", false) {
                    logger.info { "${first.absolutePath} 开始加载" }
                    first.listDirs("""\d{6}___""".toRegex()).flatMap { second ->
                        if (second.isIgnore(start)) {
                            emptyList()
                        } else {
                            second.listDirs("""\d+""".toRegex()).filter { dir ->
                                useArtWorkInfoMapper { it.contains(dir.name.toLong()) }.not() &&
                                    dir.isIgnore(start).not()
                            }.mapNotNull { dir ->
                                dir.resolve("${dir.name}.json").let {
                                    if (it.canRead()) {
                                        it.readIllustInfo()
                                    } else {
                                        null
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 强制停止缓存
     */
    @SubCommand("cancel", "stop")
    suspend fun CommandSenderOnMessage<MessageEvent>.cancel() = getHelper().runCatching {
        cacheStop()
    }.onSuccess {
        sendMessage("任务${it}已停止")
    }.onFailure {
        sendMessage(it.toString())
    }.isSuccess

    /**
     * 检查当前缓存中不可读，删除并重新下载
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.check() = getHelper().runCatching {
        useArtWorkInfoMapper { it.keys(0L..999_999_999L) }.sorted().also {
            logger.verbose { "{${it.first()}...${it.last()}}共有 ${it.size} 个作品需要检查" }
        }.run {
            size to count { pid ->
                runCatching {
                    val dir = PixivHelperSettings.imagesFolder(pid)
                    dir.resolve("${pid}.json").also { file ->
                        if (file.exists().not()) {
                            logger.warning { "${file.absolutePath} 不可读， 文件将删除重新下载，删除结果：${file.delete()}" }
                            illustDetail(pid).illust.writeTo(file)
                        }
                    }
                    useFileInfoMapper { it.fileInfos(pid) }.filter { info ->
                        dir.resolve(Url(info.url).getFilename()).exists().not()
                    }.let { infos ->
                        downloadImageUrls(urls = infos.map { it.url }, dir = dir).forEachIndexed { index, result ->
                            result.onFailure {
                                logger.warning({ "[${infos[index]}]修复出错" }, it)
                            }.onSuccess {
                                logger.info { "[${infos[index]}]修复成功" }
                            }
                        }
                    }
                }.onFailure {
                    logger.warning({ "作品(${pid})修复出错" }, it)
                    reply("作品(${pid})修复出错, ${it.message}")
                }.isFailure
            }
        }
    }.onSuccess { (size, num) ->
        sendMessage("检查缓存完毕，总计${size}, 无法修复数: $num")
    }.onFailure {
        sendMessage(it.toString())
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

    @SubCommand
    fun ConsoleCommandSender.remove(pid: Long) {
        useArtWorkInfoMapper { it.deleteByPid(pid) }
        logger.info { "色图作品(${pid})信息将从缓存移除" }
        PixivHelperSettings.imagesFolder(pid).apply {
            listFiles()?.forEach {
                it.delete()
            }
            logger.info { "色图作品(${pid})文件夹将删除，结果${delete()}" }
        }
    }

    @SubCommand
    fun ConsoleCommandSender.delete(uid: Long) {
        useArtWorkInfoMapper { it.userArtWork(uid) }.also {
            logger.verbose { "USER(${uid})共${it.size}个作品需要删除" }
        }.forEach { info ->
            useArtWorkInfoMapper { it.deleteByPid(info.pid) }
            logger.info { "色图作品(${info.pid})[${info.title}]信息将从缓存移除" }
            PixivHelperSettings.imagesFolder(info.pid).apply {
                listFiles()?.forEach { file ->
                    file.delete()
                }
                logger.info { "色图作品(${info.pid})[${info.title}]文件夹将删除，结果${delete()}" }
            }
        }
    }
}