package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.io.*
import java.time.*

object PixivCacheCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "cache",
    description = "PIXIV缓存指令",
    overrideContext = PixivCommandArgumentContext
), PixivHelperCommand {

    private var PixivHelper.reply by PixivHelperDelegate { false }

    @SubCommand
    @Description("缓存关注推送")
    suspend fun UserCommandSender.follow() = withHelper {
        addCacheJob(name = "FOLLOW", reply = reply) { name ->
            getFollowIllusts().sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }
        "任务FOLLOW已添加"
    }

    @SubCommand
    @Description("缓存指定排行榜信息")
    suspend fun UserCommandSender.rank(mode: RankMode, date: LocalDate? = null) = withHelper {
        addCacheJob(name = "RANK[${mode.name}](${date ?: "new"})", reply = reply) { name ->
            getRank(mode = mode, date = date).sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }
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
    suspend fun UserCommandSender.range(start: LocalDate, end: LocalDate) = withHelper {
        check(start <= end) { "start 要在 end 之前" }
        for (date in start..end) {
            addCacheJob(name = "RANGE{${start}~${end}}-MONTH($date)", reply = reply) { name ->
                getRank(mode = RankMode.MONTH, date = date, limit = TASK_LOAD).notCached().sendOnCompletion { total ->
                    "${name}处理完成, 共${total}"
                }
            }
        }
        "任务集RANGE{${start}~${end}}已添加"
    }

    @SubCommand
    @Description("缓存NaviRank榜作品")
    suspend fun UserCommandSender.navirank(year: Year? = null) = withHelper {
        addCacheJob(name = "NAVIRANK${year?.let { "[${it}]" } ?: ""}", reply = reply) { name ->
            getNaviRank(year = year).sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }
        "任务NAVIRANK已添加"
    }

    @SubCommand
    @Description("从推荐画师的预览中缓存色图作品，ERO过滤")
    suspend fun UserCommandSender.recommended() = withHelper {
        addCacheJob(name = "RECOMMENDED", reply = reply) { name ->
            getRecommended().eros().sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }
        "任务RECOMMENDED已添加"
    }

    @SubCommand
    @Description("从指定用户的收藏中缓存色图作品")
    suspend fun UserCommandSender.bookmarks(uid: Long? = null) = withHelper {
        addCacheJob(name = "BOOKMARKS(${uid ?: "me"})", reply = reply) { name ->
            getBookmarks(uid = uid ?: info().user.uid).sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }
        "任务BOOKMARKS(${uid ?: "me"})已添加"
    }

    @SubCommand
    @Description("缓存别名画师列表作品")
    suspend fun UserCommandSender.alias() = withHelper {
        val list = AliasSetting.all()

        addCacheJob(name = "ALIAS", reply = reply) { name ->
            getAliasUserIllusts(list = list).sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }

        "别名列表中共${list.size}个画师需要缓存"
    }

    @SubCommand
    @Description("将关注画师列表检查，缓存所有作品")
    suspend fun UserCommandSender.following(flush: Boolean = false, uid: Long? = null) = withHelper {
        val detail = userDetail(uid = uid ?: info().user.uid)

        addCacheJob(name = "FOLLOW_ALL(${detail.user.id})", reply = reply) { name ->
            getUserFollowing(detail = detail, flush = flush).sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }

        "@${detail.user.name}关注列表中共${detail.profile.totalFollowUsers}个画师需要缓存"
    }

    @SubCommand("fms")
    @Description("将关注画师列表检查，缓存所有画师收藏作品，ERO过滤")
    suspend fun UserCommandSender.followingWithMarks(jump: Int = 0, uid: Long? = null) = withHelper {
        val detail = userDetail(uid = uid ?: info().user.uid)

        addCacheJob(name = "FOLLOW_MARKS(${detail.user.id})", write = false, reply = reply) { name ->
            getUserFollowingMark(detail = detail, jump = jump).eros().sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }

        "@${detail.user.name}关注列表中共${detail.profile.totalFollowUsers}个画师的收藏需要缓存"
    }

    @SubCommand
    @Description("缓存指定画师作品")
    suspend fun UserCommandSender.user(uid: Long) = withHelper {
        val detail = userDetail(uid = uid).apply { twitter() }

        addCacheJob(name = "USER(${uid})", reply = reply) { name ->
            getUserIllusts(detail = detail).sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }

        "画师[${detail.user.name}]有${detail.profile.totalArtwork}个作品需要缓存"
    }

    @SubCommand
    @Description("缓存搜索得到的tag，ERO过滤")
    suspend fun UserCommandSender.tag(tag: String) = withHelper {
        addCacheJob(name = "TAG(${tag})", reply = reply) { name ->
            getSearchTag(tag = tag).eros().sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }
        "任务TAG(${tag})已添加"
    }

    @SubCommand
    @Description("加载缓存文件夹中未保存的作品")
    suspend fun UserCommandSender.local(range: LongRange = MAX_RANGE) = withHelper {
        addCacheJob(name = "LOCAL(${range})", write = false, reply = reply) { name ->
            getLocalCache(range = range).sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }
    }

    @SubCommand
    @Description("加载动图作品")
    suspend fun UserCommandSender.ugoira() = withHelper {
        for (range in ALL_RANGE) {
            if (isActive.not()) break
            val artworks = ArtWorkInfo.type(range, WorkContentType.UGOIRA)
            val eros = artworks.filter {
                it.ero && UgoiraImagesFolder.resolve("${it.pid}.gif").exists().not()
            }
            if (eros.isEmpty()) continue
            logger.info { "ugoira (${range})${eros.map { it.pid }}共${eros.size}个GIF需要build" }
            for (artwork in eros) {
                if (isActive.not()) break
                try {
                    getIllustInfo(pid = artwork.pid, flush = false).getUgoira()
                } catch (e: Throwable) {
                    if (DELETE_REGEX in e.message.orEmpty()) {
                        ArtWorkInfo.delete(pid = artwork.pid, comment = e.message.orEmpty())
                    }
                    logger.warning { "ugoira build ${artwork.pid} ${e.message}" }
                }
            }
            logger.info { "$range Build 完毕" }
            System.gc()
        }
        "UGOIRA Build 完毕"
    }

    private val FILE_REGEX = """(\d+)_p(\d+)\.(jpg|png)""".toRegex()

    private fun exists(source: File) = source.renameTo(ExistsImagesFolder.resolve(source.name))

    private fun other(source: File) = source.renameTo(OtherImagesFolder.resolve(source.name))

    @SubCommand
    @Description("加载临时文件夹中未保存的作品")
    suspend fun UserCommandSender.temp(path: String = "") = withHelper {
        val set = HashSet<Long>()
        val temp = if (path.isEmpty()) TempFolder else File(path)
        logger.info { "从 ${temp.absolutePath} 加载文件" }
        for (source in temp.listFiles { source -> source.isFile }.orEmpty()) {
            FILE_REGEX.find(source.name)
                ?.destructured?.let { (id) -> set.add(id.toLong()) }
                ?: other(source)
        }

        addCacheJob(name = "TEMP(${temp.absolutePath})", reply = reply) { name ->
            getListIllusts(set = set, flush = true)
                .onEach { list ->
                    for (illust in list) {
                        illust.getImages()
                    }
                }
                .sendOnCompletion { total ->
                    "${name}处理完成, 共${total}"
                }.onCompletion {
                    for (source in temp.listFiles { source -> source.isFile }.orEmpty()) {
                        exists(source)
                    }
                }
        }
        "临时文件夹${temp.absolutePath}有${set.size}个作品需要缓存"
    }

    @SubCommand
    @Description("加载缓存中有色图作品的用户的其他作品")
    suspend fun UserCommandSender.count() = withHelper {
        val records = StatisticUserInfo.list(range = 3..PAGE_SIZE)

        addCacheJob(name = "USER_ERO_COUNT", reply = reply) { name ->
            getCacheUser(records = records).sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }
        "开始加载${records.size}个缓存用户"
    }

    @SubCommand
    @Description("缓存搜索记录")
    suspend fun UserCommandSender.search() = withHelper {
        val list = PixivSearchResult.noCached()

        addCacheJob(name = "SEARCH", reply = reply) { name ->
            getListIllusts(info = list, check = false).sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }

        "搜索结果有${list.size}个作品需要缓存"
    }

    @SubCommand
    @Description("缓存漫游，ERO过滤")
    suspend fun UserCommandSender.walkthrough(times: Int = 1) = withHelper {
        addCacheJob(name = "WALK_THROUGH(${times})", reply = reply) { name ->
            getWalkThrough(times = times).eros().sendOnCompletion { total ->
                "${name}处理完成, 共${total}"
            }
        }
        "将会随机${times}次WalkThrough加载"
    }

    @SubCommand
    @Description("回复缓存细节")
    suspend fun UserCommandSender.reply(open: Boolean) = withHelper {
        reply = open
        "已设置回复状态为${reply}"
    }

    @SubCommand
    @Description("停止当前助手缓存任务")
    suspend fun UserCommandSender.stop() = withHelper {
        cacheStop(message = "指令终止")
        "任务已停止"
    }
}