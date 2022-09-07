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
        } catch (cause: Exception) {
            sendMessage("任务添加失败, ${cause.message}")
        }
    }

    @SubCommand
    @Description("缓存关注的推送")
    public suspend fun CommandSender.follow(): Unit = cache {
        val client = client()
        name = "FOLLOW(${client.uid})"
        write = false
        flow = client.follow()
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
        write = false
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

    @SubCommand
    @Description("将关注画师列表检查，缓存所有作品")
    public suspend fun CommandSender.following(flush: Boolean = false): Unit = cache {
        val client = client()
        val detail = client.userDetail(uid = client.uid)
        var index = 0
        name = "FOLLOW_ALL(${detail.user.id})"
        write = false
        flow = client.preview(detail = detail).transform { previews ->
            for (preview in previews) {
                index++
                if (flush || Twitter[preview.user.id].isEmpty()) {
                    val author = client.userDetail(uid = preview.user.id).apply { twitter() }
                    val total = author.profile.totalArtwork
                    val count = author.user.count()
                    if (total - count > preview.illusts.size || flush) {
                        logger.info { "${index}.FOLLOW_ALL(${author.user.id})[${total}]尝试缓存作品" }
                        emitAll(PixivClientPool.free().user(detail = author))
                    } else {
                        logger.info { "${index}.FOLLOW_ALL(${author.user.id})[${total}]有${preview.illusts.size}个作品尝试缓存" }
                        emit(preview.illusts)
                    }
                }
            }
        }
    }

    @SubCommand("fwm")
    @Description("将关注画师列表检查，缓存所有画师收藏作品，ERO过滤")
    public suspend fun CommandSender.followingWithMarks(jump: Long = 0): Unit = cache {
        val client = client()
        val detail = client.userDetail(uid = client.uid)
        var index = 0
        name = "FOLLOW_MARKS(${detail.user.id})"
        write = false
        flow = client.preview(detail = detail, start = jump).transform { previews ->
            for (preview in previews) {
                index++
                logger.info { "${index}.FOLLOW_MARKS(${preview.user.id})尝试缓存收藏" }
                try {
                    var cached = 0
                    PixivClientPool.free().bookmarks(uid = preview.user.id).collect { page ->
                        if (page.all { illust(pid = it.pid).exists() }) cached++ else cached = 0
                        emit(page)

                        if (cached >= 3) throw IllegalStateException("${index}.FOLLOW_MARKS(${preview.user.id}) cached")
                    }
                } catch (_: IllegalStateException) {
                    logger.info { "${index}.FOLLOW_MARKS(${preview.user.id}) 跳过" }
                }
            }
        }
    }

    @SubCommand
    @Description("缓存指定画师作品")
    public suspend fun CommandSender.user(uid: Long): Unit = cache {
        val client = PixivClientPool.free()
        val detail = client.userDetail(uid = uid).apply { twitter() }
        name = "USER(${uid})"
        flow = client.user(detail = detail)
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

            val jobs = ArrayList<Deferred<*>>(artworks.size)
            for (artwork in artworks) {
                if (!ugoira && artwork.type == WorkContentType.UGOIRA.ordinal) continue
                try {
                    val illust = loadIllustInfo(pid = artwork.pid, flush = false)
                    jobs.add(async {
                        when (illust.type) {
                            WorkContentType.ILLUST -> PixivCacheLoader.images(illust = illust)
                            WorkContentType.UGOIRA -> PixivCacheLoader.ugoira(illust = illust)
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
                } catch (cause: Exception) {
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
    public suspend fun CommandSender.ero(range: LongRange = 3..PAGE_SIZE): Unit = cache {
        var index = 0
        name = "ERO"
        write = false
        flow = PixivClientPool.free().ero(range = range).transform { (record, author) ->
            val total = author.profile.totalArtwork
            index++
            if (total > record.count) {
                logger.info { "${index}.ERO(${author.user.id})[${author.user.name}]有${total}个作品尝试缓存" }
                emitAll(PixivClientPool.free().user(detail = author))
                delay(total.coerceAtLeast(15_000))
            }
        }
    }

    @SubCommand("ewm")
    @Description("将关注画师列表检查，缓存所有画师收藏作品")
    public suspend fun CommandSender.eroWithMarks(range: LongRange = PAGE_SIZE..Int.MAX_VALUE): Unit = cache {
        var index = 0
        name = "ERO_WITH_MARKS"
        write = false
        flow = PixivClientPool.free().ero(range = range).transform { (_, author) ->
            val total = author.profile.totalIllustBookmarksPublic
            index++
            logger.info { "${index}.ERO_WITH_MARKS(${author.user.id})[${author.user.name}]有${total}个收藏尝试缓存" }
            try {
                var cached = 0
                PixivClientPool.free().bookmarks(uid = author.user.id).collect { page ->
                    if (page.all { illust(pid = it.pid).exists() }) cached++ else cached = 0
                    emit(page)

                    if (cached >= 3) throw IllegalStateException("${index}.ERO_WITH_MARKS(${author.user.id}) cached")
                }
            } catch (_: IllegalStateException) {
                logger.info { "${index}.ERO_WITH_MARKS(${author.user.id}) 跳过" }
            }
            delay(total.coerceAtLeast(15_000))
        }
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
        } catch (cause: Exception) {
            cause.message ?: cause.toString()
        }

        sendMessage(message = message)
    }
}