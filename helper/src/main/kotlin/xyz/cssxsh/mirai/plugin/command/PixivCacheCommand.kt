package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.io.File
import java.time.*

object PixivCacheCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "cache",
    description = "PIXIV缓存指令",
    overrideContext = PixivCommandArgumentContext
) {

    @SubCommand
    @Description("缓存关注推送")
    suspend fun CommandSenderOnMessage<*>.follow() = withHelper {
        addCacheJob(name = "FOLLOW", reply = false) { getFollowIllusts().types(WorkContentType.ILLUST) }
        "任务FOLLOW已添加"
    }

    @SubCommand
    @Description("缓存指定排行榜信息")
    suspend fun CommandSenderOnMessage<*>.rank(mode: RankMode, date: LocalDate? = null) = withHelper {
        addCacheJob(name = "RANK[${mode.name}](${date ?: "new"})") { getRank(mode = mode, date = date) }
        "任务RANK[${mode.name}](${date ?: "new"})已添加"
    }

    private fun Year.days() = (1..length()).map { index -> atDay(index) }

    private fun Pair<LocalDate, LocalDate>.range() = (first.year..second.year).flatMap { year ->
        Year.of(year).days().filter { it >= first && it <= second }
    }

    @SubCommand
    @Description("参数界限为解析缓存月榜作品")
    suspend fun CommandSenderOnMessage<*>.range(start: LocalDate, end: LocalDate) = withHelper {
        check(start <= end) { "start 要在 end 之前" }
        (start to end).range().forEach { date ->
            addCacheJob(name = "RANGE{${start}~${end}}-MONTH($date)", reply = false) {
                getRank(mode = RankMode.MONTH, date = date, limit = TASK_LOAD).types(WorkContentType.ILLUST).notCached()
            }
        }
        "任务集RANGE{${start}~${end}}已添加"
    }

    @SubCommand
    @Description("缓存NaviRank榜作品")
    suspend fun CommandSenderOnMessage<*>.navirank(year: Year? = null) = withHelper {
        addCacheJob(name = "NAVIRANK${year?.let { "[${it}]" } ?: ""}", reply = false) { getNaviRank(year = year) }
        "任务NAVIRANK已添加"
    }

    @SubCommand
    @Description("从推荐画师的预览中缓存色图作品，ERO过滤")
    suspend fun CommandSenderOnMessage<*>.recommended() = withHelper {
        addCacheJob(name = "RECOMMENDED") { getRecommended().eros() }
        "任务RECOMMENDED已添加"
    }

    @SubCommand
    @Description("从指定用户的收藏中缓存色图作品")
    suspend fun CommandSenderOnMessage<*>.bookmarks(uid: Long? = null) = withHelper {
        addCacheJob(name = "BOOKMARKS(${uid ?: "me"})") { getBookmarks(uid = uid ?: info().user.uid) }
        "任务BOOKMARKS(${uid ?: "me"})已添加"
    }

    @SubCommand
    @Description("缓存别名画师列表作品")
    suspend fun CommandSenderOnMessage<*>.alias() = withHelper {
        useMappers { it.statistic.alias() }.also { list ->
            addCacheJob(name = "ALIAS", reply = false) { getAliasUserIllusts(list = list) }
        }.let {
            "别名列表中共${it.size}个画师需要缓存"
        }
    }

    @SubCommand
    @Description("将关注画师列表检查，缓存所有作品")
    suspend fun CommandSenderOnMessage<*>.following(uid: Long? = null) = withHelper {
        userDetail(uid = uid ?: info().user.uid).also {
            addCacheJob(name = "FOLLOW_ALL(${it.user.id})", reply = false) { getUserFollowing(detail = it) }
        }.let {
            "关注列表中共${it.profile.totalFollowUsers}个画师需要缓存"
        }
    }

    @SubCommand
    @Description("缓存指定画师作品")
    suspend fun CommandSenderOnMessage<*>.user(uid: Long) = withHelper {
        userDetail(uid = uid).also {
            addCacheJob(name = "USER(${uid})") { getUserIllusts(detail = it) }
        }.let {
            "画师[${it.user.name}]有${it.total()}个作品需要缓存"
        }
    }

    @SubCommand
    @Description("缓存搜索得到的tag，ERO过滤")
    suspend fun CommandSenderOnMessage<*>.tag(tag: String) = withHelper {
        addCacheJob(name = "TAG(${tag})") { getSearchTag(tag = tag).eros() }
        "任务TAG(${tag})已添加"
    }

    private val MAX_RANGE = 0..999_999_999L

    @SubCommand
    @Description("加载缓存文件夹中未保存的作品")
    suspend fun CommandSenderOnMessage<*>.local(range: LongRange = MAX_RANGE) = withHelper {
        addCacheJob(name = "LOCAL(${range})", write = false) { localCache(range = range) }
    }

    private val FILE_REGEX = """(\d+)_p(\d+)\.(jpg|png)""".toRegex()

    @SubCommand
    @Description("加载临时文件夹中未保存的作品")
    suspend fun CommandSenderOnMessage<*>.temp(path: String? = null) = withHelper {
        val list = mutableSetOf<Long>()
        val dir = if (path.isNullOrEmpty()) PixivHelperSettings.tempFolder else File(path)
        logger.verbose { "从 ${dir.absolutePath} 加载文件" }
        val exists = dir.resolve("exists").apply { mkdirs() }
        val other = dir.resolve("other").apply { mkdirs() }
        dir.listFiles()?.forEach { source ->
            FILE_REGEX.find(source.name)?.destructured?.let { (id, _) ->
                if (useMappers { it.artwork.contains(id.toLong()) }) {
                    source.renameTo(exists.resolve(source.name))
                } else {
                    list.add(id.toLong())
                }
            } ?: source.renameTo(other.resolve(source.name))
        }
        addCacheJob(name = "TEMP(${dir.absolutePath})") { getListIllusts(set = list) }
        "临时文件夹${dir.absolutePath}有${list.size}个作品需要缓存"
    }

    @SubCommand
    @Description("缓存搜索记录")
    suspend fun CommandSenderOnMessage<*>.search() = withHelper {
        useMappers { it.statistic.noCacheSearchResult() }.also {
            addCacheJob(name = "SEARCH") { getListIllusts(info = it) }
        }.let {
            "搜索结果有${it.size}个作品需要缓存"
        }
    }

    @SubCommand
    @Description("缓存漫游，ERO过滤")
    suspend fun CommandSenderOnMessage<*>.walkthrough(times: Int = 1) = withHelper {
        addCacheJob(name = "WALK_THROUGH(${times})") { getWalkThrough(times = times).eros() }
        "将会随机${times}次WalkThrough加载"
    }

    @SubCommand
    @Description("停止当前助手缓存任务")
    suspend fun CommandSenderOnMessage<*>.stop() = withHelper {
        cacheStop()
        "任务已停止"
    }
}