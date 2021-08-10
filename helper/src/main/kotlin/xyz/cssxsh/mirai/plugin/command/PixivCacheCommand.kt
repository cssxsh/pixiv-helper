package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
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

    private var PixivHelper.reply by PixivHelperDelegate { false }

    @SubCommand
    @Description("缓存关注推送")
    suspend fun CommandSenderOnMessage<*>.follow() = withHelper {
        addCacheJob(name = "FOLLOW", reply = reply) { getFollowIllusts() }
        "任务FOLLOW已添加"
    }

    @SubCommand
    @Description("缓存指定排行榜信息")
    suspend fun CommandSenderOnMessage<*>.rank(mode: RankMode, date: LocalDate? = null) = withHelper {
        addCacheJob(name = "RANK[${mode.name}](${date ?: "new"})", reply = reply) { getRank(mode = mode, date = date) }
        "任务RANK[${mode.name}](${date ?: "new"})已添加"
    }

    private operator fun LocalDate.rangeTo(other: LocalDate): Sequence<LocalDate> = sequence {
        var pos = this@rangeTo
        while (pos <= other) {
            yield(pos)
            pos = pos.plusDays(1)
        }
    }

    @SubCommand
    @Description("参数界限为解析缓存月榜作品")
    suspend fun CommandSenderOnMessage<*>.range(start: LocalDate, end: LocalDate) = withHelper {
        check(start <= end) { "start 要在 end 之前" }
        (start..end).forEach { date ->
            addCacheJob(name = "RANGE{${start}~${end}}-MONTH($date)", reply = reply) {
                getRank(mode = RankMode.MONTH, date = date, limit = TASK_LOAD).notCached()
            }
        }
        "任务集RANGE{${start}~${end}}已添加"
    }

    @SubCommand
    @Description("缓存NaviRank榜作品")
    suspend fun CommandSenderOnMessage<*>.navirank(year: Year? = null) = withHelper {
        addCacheJob(name = "NAVIRANK${year?.let { "[${it}]" } ?: ""}", reply = reply) { getNaviRank(year = year) }
        "任务NAVIRANK已添加"
    }

    @SubCommand
    @Description("从推荐画师的预览中缓存色图作品，ERO过滤")
    suspend fun CommandSenderOnMessage<*>.recommended() = withHelper {
        addCacheJob(name = "RECOMMENDED", reply = reply) { getRecommended().eros() }
        "任务RECOMMENDED已添加"
    }

    @SubCommand
    @Description("从指定用户的收藏中缓存色图作品")
    suspend fun CommandSenderOnMessage<*>.bookmarks(uid: Long? = null) = withHelper {
        addCacheJob(name = "BOOKMARKS(${uid ?: "me"})", reply = reply) { getBookmarks(uid = uid ?: info().user.uid) }
        "任务BOOKMARKS(${uid ?: "me"})已添加"
    }

    @SubCommand
    @Description("缓存别名画师列表作品")
    suspend fun CommandSenderOnMessage<*>.alias() = withHelper {
        AliasSetting.all().also { list ->
            addCacheJob(name = "ALIAS", reply = reply) { getAliasUserIllusts(list = list) }
        }.let {
            "别名列表中共${it.size}个画师需要缓存"
        }
    }

    @SubCommand
    @Description("将关注画师列表检查，缓存所有作品")
    suspend fun CommandSenderOnMessage<*>.following(uid: Long? = null, flush: Boolean = false) = withHelper {
        userDetail(uid = uid ?: info().user.uid).also {
            addCacheJob(name = "FOLLOW_ALL(${it.user.id})", reply = reply) {
                getUserFollowing(detail = it, flush = flush)
            }
        }.let {
            "@${it.user.name}关注列表中共${it.profile.totalFollowUsers}个画师需要缓存"
        }
    }

    @SubCommand("fmarks")
    @Description("将关注画师列表检查，缓存所有画师收藏作品，ERO过滤")
    suspend fun CommandSenderOnMessage<*>.followingWithMarks(uid: Long? = null, jump: Int = 0) = withHelper {
        userDetail(uid = uid ?: info().user.uid).also {
            addCacheJob(name = "FOLLOW_MARKS(${it.user.id})", write = false, reply = reply) {
                getUserFollowingMark(detail = it, jump = jump).eros()
            }
        }.let {
            "@${it.user.name}关注列表中共${it.profile.totalFollowUsers}个画师的收藏需要缓存"
        }
    }

//    @SubCommand("nocache")
//    @Description("将关注画师列表检查，缓存所有画师收藏作品，ERO过滤")
//    suspend fun CommandSenderOnMessage<*>.noCache() = withHelper {
//        useMappers { it.artwork.noCache() }.also { set ->
//            addCacheJob(name = "NO_CACHE", write = false, reply = reply) { getListIllusts(set = set, flush = false) }
//        }.let {
//            "无文件信息有${it.size}个作品需要缓存"
//        }
//    }
//
//    @SubCommand("notag")
//    @Description("将关注画师列表检查，缓存所有画师收藏作品，ERO过滤")
//    suspend fun CommandSenderOnMessage<*>.noTag() = withHelper {
//        useMappers { it.artwork.noTag() }.also { set ->
//            addCacheJob(name = "NO_TAG", write = false, reply = reply) { getListIllusts(set = set, flush = true) }
//        }.let {
//            "无标签信息有${it.size}个作品需要缓存"
//        }
//    }

    @SubCommand
    @Description("缓存指定画师作品")
    suspend fun CommandSenderOnMessage<*>.user(uid: Long) = withHelper {
        userDetail(uid = uid).also {
            addCacheJob(name = "USER(${uid})", reply = reply) { getUserIllusts(detail = it) }
        }.let {
            "画师[${it.user.name}]有${it.total()}个作品需要缓存"
        }
    }

    @SubCommand
    @Description("缓存搜索得到的tag，ERO过滤")
    suspend fun CommandSenderOnMessage<*>.tag(tag: String) = withHelper {
        addCacheJob(name = "TAG(${tag})", reply = reply) { getSearchTag(tag = tag).eros() }
        "任务TAG(${tag})已添加"
    }

    private val MAX_RANGE = 0..999_999_999L

    @SubCommand
    @Description("加载缓存文件夹中未保存的作品")
    suspend fun CommandSenderOnMessage<*>.local(range: LongRange = MAX_RANGE) = withHelper {
        addCacheJob(name = "LOCAL(${range})", write = false, reply = reply) { localCache(range = range) }
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
                if (id.toLong() in ArtWorkInfo) {
                    source.renameTo(exists.resolve(source.name))
                } else {
                    list.add(id.toLong())
                }
            } ?: source.renameTo(other.resolve(source.name))
        }
        addCacheJob(name = "TEMP(${dir.absolutePath})", reply = reply) { getListIllusts(set = list, flush = true) }
        "临时文件夹${dir.absolutePath}有${list.size}个作品需要缓存"
    }

    @SubCommand
    @Description("缓存搜索记录")
    suspend fun CommandSenderOnMessage<*>.search() = withHelper {
        PixivSearchResult.noCached().also {
            addCacheJob(name = "SEARCH", reply = reply) { getListIllusts(info = it, check = false) }
        }.let {
            "搜索结果有${it.size}个作品需要缓存"
        }
    }

    @SubCommand
    @Description("缓存漫游，ERO过滤")
    suspend fun CommandSenderOnMessage<*>.walkthrough(times: Int = 1) = withHelper {
        addCacheJob(name = "WALK_THROUGH(${times})", reply = reply) { getWalkThrough(times = times).eros() }
        "将会随机${times}次WalkThrough加载"
    }

    @SubCommand
    @Description("停止当前助手缓存任务")
    suspend fun CommandSenderOnMessage<*>.reply(open: Boolean) = withHelper {
        reply = open
        "已设置回复状态为${reply}"
    }

    @SubCommand
    @Description("停止当前助手缓存任务")
    suspend fun CommandSenderOnMessage<*>.stop() = withHelper {
        cacheStop()
        "任务已停止"
    }
}