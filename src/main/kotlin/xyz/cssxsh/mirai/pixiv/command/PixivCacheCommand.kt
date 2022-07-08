package xyz.cssxsh.mirai.pixiv.command

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.mirai.pixiv.task.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.exception.*
import java.time.*

public object PixivCacheCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "cache",
    description = "PIXIV缓存指令",
    overrideContext = PixivCommandArgumentContext
), PixivHelperCommand {

    private suspend fun CommandSender.cache(block: suspend PixivTaskBuilder.() -> Unit) {
        if ((this as? UserCommandSender)?.subject?.helper?.shake() == true) return
        try {
            val task = PixivTaskBuilder().apply { block() }.build()
            val scope = this
            PixivCacheLoader.cache(task = task) { _, cause ->
                scope.launch {
                    when (cause) {
                        null -> sendMessage("${task.name} 缓存完成")
                        is CancellationException -> sendMessage("${task.name} 缓存被终止")
                        else -> sendMessage("${task.name} 缓存出现异常")
                    }
                }
            }
            sendMessage("任务 ${task.name} 已添加")
        } catch (cause: Throwable) {
            sendMessage("任务添加失败, ${cause.message}")
        }
    }

    @SubCommand
    @Description("缓存关注的推送")
    public suspend fun CommandSender.follow(): Unit = cache {
        val client = client()
        name = "FOLLOW(${client.uid})"
        flow = client.follow()
        write = false
    }

    @SubCommand
    @Description("缓存指定排行榜信息")
    public suspend fun CommandSender.rank(mode: RankMode, date: LocalDate? = null): Unit = cache {
        val client = PixivClientPool.free()
        name = "RANK[${mode.name}](${date ?: "now"})"
        flow = client.rank(mode, date)
    }

    @SubCommand
    @Description("缓存月榜作品")
    public suspend fun CommandSender.month(start: LocalDate, end: LocalDate): Unit = cache {
        check(start <= end) { "start 要在 end 之前" }
        name = "MONTH(${start}~${end})"
        flow = flow {
            for (date in start..end) {
                val client = PixivClientPool.free()
                emitAll(client.rank(RankMode.MONTH, date))
            }
        }
    }

    @SubCommand
    @Description("缓存NaviRank榜作品")
    public suspend fun CommandSender.navirank(year: Year? = null): Unit = cache {
        name = if (year != null) "NAVIRANK[$year]" else "NAVIRANK"
        flow = PixivClientPool.free().navirank(year = year)
    }

    @SubCommand
    @Description("从推荐画师的预览中缓存色图作品")
    public suspend fun CommandSender.recommended(): Unit = cache {
        val client = client()
        name = "RECOMMENDED(${client.uid})"
        flow = client.recommended()
    }

    @SubCommand
    @Description("从指定用户的收藏中缓存色图作品")
    public suspend fun CommandSender.bookmarks(uid: Long): Unit = cache {
        name = "BOOKMARKS(${uid})"
        flow = PixivClientPool.free().bookmarks(uid = uid)
    }

//    @SubCommand
//    @Description("缓存别名画师列表作品")
//    public suspend fun CommandSender.alias() {
//        val list = AliasSetting.all()
//
//        cache(name = "ALIAS") {
//            //
//            //            getAliasUserIllusts(list = list).sendOnCompletion { total ->
//            //                "${name}处理完成, 共${total}"
//            //            }
//        }
//
//        // "别名列表中共${list.size}个画师需要缓存"
//    }

    @SubCommand
    @Description("将关注画师列表检查，缓存所有作品")
    public suspend fun CommandSender.following(flush: Boolean = false): Unit = cache {
        val client = client()
        val detail = client.userDetail(uid = client.uid)
        name = "FOLLOW_ALL(${detail.user.id})"
        // TODO
        //            getUserFollowing(detail = detail, flush = flush).sendOnCompletion { total ->
        //                "${name}处理完成, 共${total}"
        //            }
        //  "@${detail.user.name}关注列表中共${detail.profile.totalFollowUsers}个画师需要缓存"
    }

    @SubCommand("fms")
    @Description("将关注画师列表检查，缓存所有画师收藏作品，ERO过滤")
    public suspend fun CommandSender.followingWithMarks(): Unit = cache {
        val client = client()
        val detail = client.userDetail(uid = client.uid)
        name = "FOLLOW_MARKS(${detail.user.id})"
        // TODO
        //            getUserFollowingMark(detail = detail, jump = jump).sendOnCompletion { total ->
        //                "${name}处理完成, 共${total}"
        //            }
        //            "@${detail.user.name}关注列表中共${detail.profile.totalFollowUsers}个画师的收藏需要缓存"
    }

    @SubCommand
    @Description("缓存指定画师作品")
    public suspend fun CommandSender.user(uid: Long): Unit = cache {
        val client = PixivClientPool.free()
        val detail = client.userDetail(uid = uid).apply { twitter() }
        name = "USER(${uid})"
        flow = client.user(detail = detail)
        // "画师[${detail.user.name}]有${detail.profile.totalArtwork}个作品需要缓存"
    }

    @SubCommand
    @Description("缓存搜索TAG得到的作品")
    public suspend fun CommandSender.tag(word: String): Unit = cache {
        val client = client()
        name = "TAG($word)"
        flow = client.search(tag = word)
    }

    @SubCommand
    @Description("缓存未缓存的色图作品")
    public suspend fun CommandSender.nocache(ugoira: Boolean = false): Unit = supervisorScope {
        for (range in ALL_RANGE) {
            if (isActive.not()) break
            val artworks = ArtWorkInfo.nocache(range)
            if (artworks.isEmpty()) continue
            logger.info { "NOCACHE(${range})共${artworks.size}个ArtWork需要Cache" }

            val jobs = ArrayList<Deferred<*>>()
            for (artwork in artworks) {
                if (!ugoira && artwork.type == WorkContentType.UGOIRA.ordinal) continue
                try {
                    val illust = loadIllustInfo(pid = artwork.pid, flush = false)
                    jobs.add(async {
                        when (illust.type) {
                            WorkContentType.ILLUST -> PixivCacheLoader.images(illust = illust)
                            WorkContentType.UGOIRA -> illust.getUgoira()
                            WorkContentType.MANGA -> Unit
                        }
                    })
                } catch (cause: AppApiException) {
                    if (DELETE_REGEX in cause.message) {
                        ArtWorkInfo.delete(pid = artwork.pid, comment = cause.message)
                    } else {
                        logger.warning({ "NOCACHE 加载作品(${artwork.pid})失败" }, cause)
                    }
                } catch (cause: RestrictException) {
                    ArtWorkInfo.delete(pid = artwork.pid, comment = cause.message)
                    logger.warning { "NOCACHE 加载作品失败 ${cause.illust}" }
                } catch (cause: Throwable) {
                    logger.warning({ "NOCACHE 加载作品(${artwork.pid})失败" }, cause)
                }
            }

            try {
                jobs.awaitAll()
            } catch (_: CancellationException) {
                //
            }

            logger.info { "$range Cache 完毕" }
        }

        sendMessage(message = "Cache 完毕")
    }

    @SubCommand
    @Description("加载缓存中有色图作品的用户的其他作品")
    public suspend fun CommandSender.count(range: LongRange = 3..PAGE_SIZE): Unit = cache {
        val records = runInterruptible(Dispatchers.IO) {
            StatisticUserInfo.list(range = range)
        }
        name = "USER_ERO_COUNT"
        // TODO
        //                getCacheUser(records = records).sendOnCompletion { total ->
        //                    "${name}处理完成, 共${total}"
        //                }
        //  "开始加载${records.size}个缓存用户"
    }

    @SubCommand("cms")
    @Description("将关注画师列表检查，缓存所有画师收藏作品，ERO过滤")
    public suspend fun CommandSender.countWithMarks(range: LongRange = PAGE_SIZE..Int.MAX_VALUE): Unit = cache {
        val records = runInterruptible(Dispatchers.IO) {
            StatisticUserInfo.list(range = range)
        }
        name = "USER_ERO_COUNT_MARKS"
        // TODO
        //                getCacheUserMarks(records = records).sendOnCompletion { total ->
        //                    "${name}处理完成, 共${total}"
        //                }
        //  "开始加载${records.size}个缓存用户的收藏"
    }

    @SubCommand
    @Description("缓存搜索记录")
    public suspend fun CommandSender.search(): Unit = cache {
        val records = runInterruptible(Dispatchers.IO) {
            PixivSearchResult.noCached()
        }
        name = "SEARCH"
        flow = PixivClientPool.free().illusts(targets = records.mapTo(HashSet()) { it.pid })
        //  "搜索结果有${list.size}个作品需要缓存"
    }

    @SubCommand
    @Description("缓存任务详情")
    public suspend fun CommandSender.detail() {
        sendMessage(message = PixivCacheLoader.detail().ifEmpty { "任务列表为空" })
    }

    @SubCommand
    @Description("停止缓存任务")
    public suspend fun CommandSender.stop(name: String) {
        val message = try {
            PixivCacheLoader.stop(name = name)
            "任务 $name 将终止"
        } catch (cause: Throwable) {
            cause.message ?: cause.toString()
        }

        sendMessage(message = message)
    }
}