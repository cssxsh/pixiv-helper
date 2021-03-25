package xyz.cssxsh.mirai.plugin.command

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
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

    private fun loadDayOfYears(year: Year, interval: Int = 5, offset: Long = 29) = buildList {
        (1..year.length() step interval).forEach { dayOfYear ->
            add(year.atDay(dayOfYear).plusDays(offset))
        }
    }

    @SubCommand
    @Description("缓存关注推送")
    suspend fun CommandSenderOnMessage<MessageEvent>.follow() =
        getHelper().addCacheJob(name = "FOLLOW", reply = false) { getFollowIllusts().map { it.nomanga() } }

    @SubCommand
    @Description("缓存指定排行榜信息")
    suspend fun CommandSenderOnMessage<MessageEvent>.rank(mode: RankMode, date: LocalDate? = null) =
        getHelper().addCacheJob(name = "RANK[${mode.name}](${date ?: "new"})") { getRank(mode = mode, date = date) }

    @SubCommand
    @Description("以年为界限缓存月榜作品")
    suspend fun CommandSenderOnMessage<MessageEvent>.year(year: Year) = getHelper().run {
        loadDayOfYears(year).forEach { date ->
            addCacheJob(name = "YEAR[${date.year}]-MONTH($date)", reply = false) {
                getRank(mode = RankMode.MONTH, date = date, limit = 90).map { it.nomanga().notCached() }
            }
        }
    }

    @SubCommand
    @Description("从推荐画师的预览中缓存色图作品")
    suspend fun CommandSenderOnMessage<MessageEvent>.recommended() =
        getHelper().addCacheJob(name = "RECOMMENDED") { getRecommended() }

    @SubCommand
    @Description("从指定用户的收藏中缓存色图作品")
    suspend fun CommandSenderOnMessage<MessageEvent>.bookmarks(uid: Long) =
        getHelper().addCacheJob(name = "BOOKMARKS") { getBookmarks(uid) }

    @SubCommand
    @Description("缓存别名画师列表作品")
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
    @Description("将关注画师列表检查，缓存所有作品")
    suspend fun CommandSenderOnMessage<MessageEvent>.following(): Unit = getHelper().run {
        getUserFollowingPreview(detail = userDetail(uid = getAuthInfo().user.uid).apply {
            logger.verbose { "关注中共${profile.totalFollowUsers}个画师需要缓存" }
            sendMessage("关注列表中共${profile.totalFollowUsers}个画师需要缓存")
        }).also { flow ->
            addCacheJob(name = "FOLLOW_ALL(${getAuthInfo().user.uid})", reply = false) {
                var index = 0
                flow.transform { list ->
                    list.forEach { preview ->
                        index++
                        if (preview.isLoaded().not()) {
                            val detail = userDetail(uid = preview.user.id)
                            if (detail.total() > detail.count() + preview.illusts.size) {
                                logger.verbose { "${index}.USER(${detail.user.id})有${detail.total()}个作品尝试缓存" }
                                emitAll(getUserIllusts(detail))
                            } else {
                                logger.verbose { "${index}.USER(${detail.user.id})有${preview.illusts.size}个作品尝试缓存" }
                                emit(preview.illusts)
                            }
                        }
                    }
                }
            }
        }
    }

    @SubCommand
    @Description("缓存指定画师作品")
    suspend fun CommandSenderOnMessage<MessageEvent>.user(uid: Long) =
        getHelper().addCacheJob(name = "USER(${uid})") { getUserIllusts(uid = uid) }

    private fun File.listDirs(regex: Regex, range: LongRange) =
        listFiles { file -> file.name.matches(regex) && file.isDirectory && file.isContained(range) }.orEmpty()

    private fun File.isContained(range: LongRange) =
        name.replace('_', '0').toLong() <= range.last && name.replace('_', '9').toLong() >= range.first

    private val MAX_RANGE = 0..999_999_999L

    @SubCommand
    @Description("加载缓存文件夹中未保存的作品")
    suspend fun CommandSenderOnMessage<MessageEvent>.local(range: LongRange = MAX_RANGE): Unit = getHelper().run {
        PixivHelperSettings.cacheFolder.also {
            logger.verbose { "从 ${it.absolutePath} 加载作品信息" }
        }.listDirs("""\d{3}______""".toRegex(), range).forEach { first ->
            addCacheJob(name = "LOCAL(${first.name})", write = false) {
                logger.info { "${first.absolutePath} 开始加载" }
                first.listDirs("""\d{6}___""".toRegex(), range).map { second ->
                    second.listDirs("""\d+""".toRegex(), range).filter { dir ->
                        useMappers { it.artwork.contains(dir.name.toLong()) }.not()
                    }.mapNotNull { dir ->
                        dir.resolve("${dir.name}.json").takeIf { it.canRead() }?.readIllustInfo()
                    }
                }.asFlow()
            }
        }
    }

    private val FILE_REGEX = """(\d+)_p(\d+)\.(jpg|png)""".toRegex()

    @SubCommand
    @Description("加载临时文件夹中未保存的作品")
    suspend fun CommandSenderOnMessage<MessageEvent>.temp(path: String = ""): Unit = getHelper().run {
        val list = mutableSetOf<Long>()
        val dir = if (path.isNotBlank()) File(path) else PixivHelperSettings.tempFolder
        val exists = PixivHelperSettings.tempFolder.resolve("exists").apply { mkdirs() }
        logger.verbose { "从 ${dir.absolutePath} 加载文件" }
        dir.listFiles()?.forEach { source ->
            FILE_REGEX.find(source.name)?.destructured?.let { (id, _) ->
                if (useMappers { it.artwork.contains(id.toLong()) }) {
                    source.renameTo(exists.resolve(source.name))
                } else {
                    list.add(id.toLong())
                }
            }
        }
        addCacheJob(name = "TEMP(${dir.absolutePath})") {
            getListIllusts(set = list)
        }
    }

    @SubCommand
    @Description("缓存搜索记录")
    suspend fun CommandSenderOnMessage<MessageEvent>.search(): Unit = getHelper().run {
        addCacheJob(name = "SEARCH") {
            PixivSearchData.results.map { (_, result) -> result.pid }.filter { pid ->
                useMappers { it.artwork.contains(pid) }.not()
            }.let {
                getListIllusts(set = it.toSet())
            }
        }
    }

    @SubCommand
    @Description("停止当前助手缓存任务")
    suspend fun CommandSenderOnMessage<MessageEvent>.stop() = getHelper().runCatching {
        cacheStop()
    }.onSuccess {
        sendMessage("任务已停止")
    }.onFailure {
        sendMessage(it.toString())
    }.isSuccess

    @SubCommand
    @Description("检查当前缓存中不可读，删除并重新下载")
    suspend fun CommandSenderOnMessage<MessageEvent>.check(interval: LongRange) = getHelper().runCatching {
        useMappers { it.artwork.artWorks(interval) }.sortedBy { it.pid }.also {
            logger.verbose { "{${it.first().pid..it.last().pid}}共有 ${it.size} 个作品需要检查" }
        }.groupBy { info ->
            isActive && PixivHelperSettings.imagesFolder(info.pid).runCatching {
                resolve("${info.pid}.json").also { file ->
                    if (file.exists().not()) {
                        logger.warning { "${file.absolutePath} 不可读， 文件将删除重新下载，删除结果：${file.delete()}" }
                        illustDetail(info.pid).illust.writeToCache().saveToSQLite()
                    }
                }
                useMappers { it.file.fileInfos(info.pid) }.filter { info ->
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
                send {
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