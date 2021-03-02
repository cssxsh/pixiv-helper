package xyz.cssxsh.mirai.plugin.command

import io.ktor.http.*
import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.api.apps.*
import java.io.File
import java.time.*

@Suppress("unused")
object PixivCacheCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "cache",
    description = "PIXIV缓存指令",
    overrideContext = PixivCommandArgumentContext
) {

    private val CACHE_RANKS = listOf(
        RankMode.MONTH,
        RankMode.WEEK,
        RankMode.WEEK_ORIGINAL,
        RankMode.DAY,
        RankMode.DAY_MALE,
        RankMode.DAY_FEMALE,
    )

    private fun loadDayOfYears(year: Year, interval: Int = 5, offset: Long = 29) = buildList {
        (1..year.length() step interval).forEach { dayOfYear ->
            add(year.atDay(dayOfYear).plusDays(offset))
        }
    }

    /**
     * 缓存关注列表
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.follow() = getHelper().run {
        getFollowIllusts().nomanga().groupBy { it.createAt.toLocalDate() }.forEach { (date, list) ->
            addCacheJob(name = "FOLLOW(${date})", reply = false) { list }
        }
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.ranks(date: LocalDate? = null) = getHelper().run {
        CACHE_RANKS.forEach { mode ->
            addCacheJob(name = "RANK[${mode.name}](${date ?: "new"})") {
                getRank(mode = mode, date = date)
            }
        }
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.rank(mode: RankMode, date: LocalDate? = null) = getHelper().run {
        addCacheJob(name = "RANK[${mode.name}](${date ?: "new"})") { getRank(mode = mode, date = date, limit = 120) }
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.year(year: Year) = getHelper().run {
        loadDayOfYears(year).forEach { date ->
            addCacheJob(name = "YEAR[${date.year}]-MONTH($date)", reply = false) {
                getRank(mode = RankMode.MONTH, date = date, limit = 90).nomanga().nocache()
            }
        }
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.recommended() =
        getHelper().addCacheJob(name = "RECOMMENDED") { getRecommended().flatMap { it.illusts }.filter { it.isEro() } }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.bookmarks(uid: Long) =
        getHelper().addCacheJob(name = "BOOKMARKS") { getBookmarks(uid) }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.alias(): Unit = getHelper().run {
        PixivAliasData.aliases.values.toSet().sorted().also { list ->
            logger.verbose { "别名中{${list.first()..list.last()}}共${list.size}个画师需要缓存" }
            sendMessage("别名列表中共${list.size}个画师需要缓存")
            launch {
                list.forEachIndexed { index, uid ->
                    if (isActive) runCatching {
                        userDetail(uid = uid).let { detail ->
                            if (detail.total() > detail.count()) {
                                logger.verbose { "${index}.USER(${uid})有${detail.total()}个作品尝试缓存" }
                                addCacheJob(name = "${index}.USER(${uid})", reply = false) {
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
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.followAll(): Unit = getHelper().run {
        getUserFollowingPreview().sortedBy { it.user.id }.also { list ->
            logger.verbose { "关注中{${list.first().user.id..list.last().user.id}}共${list.size}个画师需要缓存" }
            sendMessage("关注列表中共${list.size}个画师需要缓存")
            launch {
                list.forEachIndexed { index, preview ->
                    if (isActive) runCatching {
                        if (preview.isLoaded().not()) {
                            userDetail(uid = preview.user.id).let { detail ->
                                if (detail.total() > detail.count() + preview.illusts.size) {
                                    logger.verbose { "${index}.USER(${detail.user.id})有${detail.total()}个作品尝试缓存" }
                                    addCacheJob(name = "${index}.USER(${detail.user.id})", reply = false) {
                                        getUserIllusts(detail)
                                    }
                                } else {
                                    logger.verbose { "${index}.USER(${detail.user.id})有${preview.illusts.size}个作品尝试缓存" }
                                    addCacheJob(name = "${index}.USER_PREVIEW(${detail.user.id})", reply = false) {
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
    }

    /**
     * 从用户详情加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.user(uid: Long) =
        getHelper().addCacheJob(name = "USER(${uid})") { getUserIllusts(uid = uid) }

    private fun File.listDirs(regex: Regex) =
        listFiles { file -> file.name.matches(regex) && file.isDirectory }.orEmpty()

    private fun File.isContained(range: LongRange) =
        name.replace('_', '0').toLong() <= range.last && name.replace('_', '9').toLong() >= range.first

    private val MAX_RANGE = 0..999_999_999L

    /**
     * 从文件夹中加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.load(range: LongRange = MAX_RANGE) = getHelper().run {
        PixivHelperSettings.cacheFolder.also {
            logger.verbose { "从 ${it.absolutePath} 加载作品信息" }
        }.listDirs("""\d{3}______""".toRegex()).forEach { first ->
            if (first.isContained(range)) {
                addCacheJob(name = "LOAD(${first.name})", write = false) {
                    logger.info { "${first.absolutePath} 开始加载" }
                    first.listDirs("""\d{6}___""".toRegex()).flatMap { second ->
                        if (second.isContained(range)) {
                            second.listDirs("""\d+""".toRegex()).filter { dir ->
                                useArtWorkInfoMapper { it.contains(dir.name.toLong()) }.not() &&
                                    dir.isContained(range)
                            }.mapNotNull { dir ->
                                dir.resolve("${dir.name}.json").takeIf { it.canRead() }?.readIllustInfo()
                            }
                        } else {
                            emptyList()
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
        sendMessage("任务已停止")
    }.onFailure {
        sendMessage(it.toString())
    }.isSuccess

    /**
     * 检查当前缓存中不可读，删除并重新下载
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.check(interval: LongRange) = getHelper().runCatching {
        useArtWorkInfoMapper { it.artWorks(interval) }.sortedBy { it.pid }.also {
            logger.verbose { "{${it.first().pid..it.last().pid}}共有 ${it.size} 个作品需要检查" }
        }.groupBy { info ->
            isActive && PixivHelperSettings.imagesFolder(info.pid).runCatching {
                resolve("${info.pid}.json").also { file ->
                    if (file.exists().not()) {
                        logger.warning { "${file.absolutePath} 不可读， 文件将删除重新下载，删除结果：${file.delete()}" }
                        illustDetail(info.pid).illust.apply { writeToCache() }.saveToSQLite()
                    }
                }
                useFileInfoMapper { it.fileInfos(info.pid) }.filter { info ->
                    resolve(Url(info.url).getFilename()).exists().not()
                }.let { infos ->
                    PixivHelperDownloader.downloadImages(
                        urls = infos.map { it.url },
                        dir = this,
                    ).forEachIndexed { index, result ->
                        result.onFailure {
                            logger.warning({ "[${infos[index]}]修复出错" }, it)
                        }.onSuccess {
                            logger.info { "[${infos[index]}]修复成功" }
                        }
                    }
                }
            }.onFailure {
                logger.warning({ "作品(${info.pid})修复出错" }, it)
                sign {
                    "作品(${info.pid})修复出错, ${it.message}"
                }
            }.isSuccess
        }
    }.onSuccess { (success, failure) ->
        sendMessage("检查缓存完毕，成功数: ${success.orEmpty().size}, 失败数: ${failure.orEmpty().size}")
    }.onFailure {
        sendMessage(it.toString())
    }.isSuccess
}