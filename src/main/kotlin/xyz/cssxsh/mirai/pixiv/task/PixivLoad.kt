package xyz.cssxsh.mirai.pixiv.task

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.mirai.pixiv.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.exception.*
import java.time.*

public typealias IllustPage = List<IllustInfo>

public typealias IllustFlow = Flow<IllustPage>

public typealias TaskCompletionHandler = (task: PixivCacheTask, cause: Throwable?) -> Unit

public data class PixivCacheTask(
    public val name: String,
    public val flow: IllustFlow,
    public val write: Boolean = false,
    public val download: Boolean = true
)

public inline fun buildPixivCacheTask(block: PixivTaskBuilder.() -> Unit): PixivCacheTask {
    return PixivTaskBuilder().apply(block).build()
}

public class PixivTaskBuilder {
    public var name: String = ""
    public var flow: IllustFlow = emptyFlow()
    public var write: Boolean = true
    public var download: Boolean = true

    public fun build(): PixivCacheTask {
        check(name.isNotBlank()) { "task name is blank" }
        check(flow != emptyFlow<IllustInfo>()) { "task flow is empty" }
        return PixivCacheTask(
            name = name,
            flow = flow
        )
    }

    private fun months(year: Year?) = buildList {
        var temp = year?.atMonth(1) ?: NaviRank.START
        val limit = minOf(year?.atMonth(12) ?: YearMonth.now(), YearMonth.now())
        while (temp <= limit) {
            add(temp)
            temp = temp.plusMonths(1)
        }
    }

    public operator fun LocalDate.rangeTo(other: LocalDate): Sequence<LocalDate> = sequence {
        var pos = this@rangeTo
        while (pos <= other) {
            yield(pos)
            pos = pos.plusDays(1)
        }
    }

    public fun PixivAppClient.illusts(targets: Set<Long>, flush: Boolean = false): IllustFlow = flow {
        val cache = mutableListOf<IllustInfo>()
        for (pid in targets) {
            try {
                cache.clear()
                cache.add(loadIllustInfo(pid = pid, flush = flush, client = this@illusts))
            } catch (cause: AppApiException) {
                if (DELETE_REGEX in cause.message) {
                    ArtWorkInfo(pid = pid, caption = cause.message).merge()
                } else {
                    logger.warning({ "加载作品($pid)失败" }, cause)
                }
            } catch (cause: RestrictException) {
                cause.illust.toArtWorkInfo().copy(pid = pid, caption = cause.message).merge()
            } catch (_: CancellationException) {
                break
            }

            if (cache.size > PAGE_SIZE) emit(cache)
        }
        if (cache.isNotEmpty()) emit(cache)
    }

    public fun PixivClientPool.AuthClient.follow(): IllustFlow = flow {
        var page = 0
        for (offset in 0 until FOLLOW_LIMIT step PAGE_SIZE) {
            try {
                val illusts = illustFollow(offset = offset).illusts
                if (illusts.isEmpty()) break
                emit(illusts)
                logger.verbose { "加载用户(${uid})关注用户作品时间线第${page++}页{${illusts.size}}成功" }
            } catch (_: CancellationException) {
                break
            } catch (cause: Exception) {
                logger.warning({ "加载用户(${uid})关注用户作品时间线第${page++}页失败" }, cause)
            }
        }
    }

    public fun PixivAppClient.rank(mode: RankMode, date: LocalDate? = null): IllustFlow = flow {
        var page = 0
        for (offset in 0 until LOAD_LIMIT step PAGE_SIZE) {
            try {
                val illusts = illustRanking(mode = mode, date = date, offset = offset).illusts
                if (illusts.isEmpty()) break
                emit(illusts)
                logger.verbose { "加载排行榜[${mode}](${date ?: "new"})第${page++}页{${illusts.size}}成功" }
            } catch (_: CancellationException) {
                break
            } catch (cause: Exception) {
                logger.warning({ "加载排行榜[${mode}](${date ?: "new"})第${page++}页失败" }, cause)
            }
        }
    }

    public fun PixivAppClient.navirank(year: Year?): IllustFlow = flow {
        for (month in months(year = year)) {
            try {
                val targets = HashSet<Long>()
                for (record in NaviRank.getAllRank(month = month).records) {
                    targets.add(record.pid)
                }
                for ((_, records) in NaviRank.getOverRank(month = month).records) {
                    for (record in records) {
                        targets.add(record.pid)
                    }
                }
                emitAll(illusts(targets = targets, flush = true))
                logger.verbose { "加载 NaviRank[$month]{${targets.size}}成功" }
            } catch (_: CancellationException) {
                break
            } catch (cause: Exception) {
                logger.warning({ "加载 NaviRank[$month]失败" }, cause)
            }
        }
    }

    public fun PixivClientPool.AuthClient.recommended(): IllustFlow = flow {
        var page = 0
        val cache = HashMap<Long, IllustInfo>()
        for (offset in 0 until RECOMMENDED_LIMIT step PAGE_SIZE) {
            try {
                cache.clear()
                val data = illustRecommended(offset = offset)
                data.illusts.associateByTo(cache) { it.pid }
                data.rankingIllusts.associateByTo(cache) { it.pid }
                if (cache.isEmpty()) break
                emit(cache.values.toList())
                logger.verbose { "加载用户(${uid})推荐作品第${page++}页{${cache.size}}成功" }
            } catch (_: CancellationException) {
                break
            } catch (cause: Exception) {
                logger.warning({ "加载用户(${uid})推荐作品第${page++}页失败" }, cause)
            }
        }
    }

    public fun PixivAppClient.bookmarks(uid: Long, tag: String? = null): IllustFlow = flow {
        var url = USER_BOOKMARKS_ILLUST
        for (offest in 0 until LOAD_LIMIT step PAGE_SIZE) {
            try {
                val (illusts, nextUrl) = userBookmarksIllust(uid = uid, tag = tag, url = url)
                url = nextUrl ?: break
                emit(illusts)
                logger.verbose { "加载用户(${uid})收藏页{${illusts.size}} ${url}成功" }
            } catch (_: CancellationException) {
                break
            } catch (cause: Exception) {
                logger.warning({ "加载用户(${uid})收藏页${url}失败" }, cause)
                break
            }
        }
    }

    public fun PixivAppClient.user(detail: UserDetail): IllustFlow = flow {
        logger.verbose { "画师[${detail.user.name}]有${detail.profile.totalArtwork}个作品需要缓存" }
        var page = 0
        for (offset in 0 until detail.profile.totalArtwork step PAGE_SIZE) {
            try {
                val illusts = userIllusts(uid = detail.user.id, offset = offset).illusts
                if (illusts.isEmpty()) break
                emit(illusts)
                logger.verbose { "加载用户(${detail.user.id})作品第${page++}页{${illusts.size}}成功" }
            } catch (_: CancellationException) {
                break
            } catch (cause: Exception) {
                logger.warning({ "加载用户(${detail.user.id})作品第${page++}页失败" }, cause)
            }
        }
    }

    public fun PixivAppClient.search(tag: String): IllustFlow = flow {
        val word = tag.split(delimiters = TAG_DELIMITERS.toCharArray()).joinToString(" ")
        var page = 0
        for (offset in 0 until SEARCH_LIMIT step PAGE_SIZE) {
            try {
                val illusts = searchIllust(word = word, offset = offset).illusts
                if (illusts.isEmpty()) break
                emit(illusts)
                logger.verbose { "加载'${tag}'搜索列表第${page++}页{${illusts.size}}成功" }
            } catch (_: CancellationException) {
                break
            } catch (cause: Exception) {
                logger.warning({ "加载'${tag}'搜索列表第${page++}页失败" }, cause)
            }
        }
    }

    public fun PixivAppClient.ero(range: LongRange): Flow<Pair<StatisticUserInfo, UserDetail>> = flow {
        val records = runInterruptible(Dispatchers.IO) {
            StatisticUserInfo.list(range = range)
        }
        logger.info { "关注中共缓存中 ${records.size} 个色图画师" }
        for (record in records) {
            try {
                val author = userDetail(uid = record.uid).apply { twitter() }
                emit(record to author)
            } catch (_: CancellationException) {
                break
            } catch (cause: Exception) {
                logger.warning({ "${record}加载失败" }, cause)
            }
        }
    }

    public fun PixivAppClient.preview(detail: UserDetail, start: Long = 0): Flow<List<UserPreview>> = flow {
        logger.info { "关注中共有 ${detail.profile.totalFollowUsers} 个画师" }
        var page = 0
        for (offset in start until detail.profile.totalFollowUsers step PAGE_SIZE) {
            try {
                val previews = userFollowing(uid = detail.user.id, offset = offset).previews
                if (previews.isEmpty()) break
                emit(previews)
                logger.verbose { "加载用户(${detail.user.id})关注用户第${page++}页{${previews.size}}成功" }
            } catch (_: CancellationException) {
                break
            } catch (cause: Exception) {
                logger.warning({ "加载用户(${detail.user.id})关注用户第${page++}页失败" }, cause)
            }
        }
    }
}
//
//internal suspend fun getBookmarksRandom(detail: UserDetail, tag: String? = null): IllustData {
//    val max = (0..detail.profile.totalIllustBookmarksPublic).random() + PAGE_SIZE
//    return PixivClientPool.free().userBookmarksIllust(uid = detail.user.id, tag = tag, max = max).apply {
//        check(illusts.isEmpty()) { "随机收藏USER[${detail.user.id}]<${tag}>失败" }
//    }
//}
//
//internal fun PixivHelper.getBookmarkTagInfos(limit: Long = BOOKMARK_TAG_LIMIT) = flow {
//    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
//        if (active().not()) return@flow
//        runCatching {
//            userBookmarksTagsIllust(offset = offset).tags
//        }.onSuccess {
//            if (it.isEmpty()) return@flow
//            emit(it)
//            logger.verbose { "加载收藏标签第${page}页{${it.size}}成功" }
//        }.onFailure {
//            logger.warning({ "加载收藏标签第${page}页失败" }, it)
//        }
//    }
//}
//
//internal fun getAliasUserIllusts(list: Collection<AliasSetting>) = flow {
//    val records = HashSet<Long>()
//    for ((alias, uid) in list) {
//        if (active().not()) break
//        if (uid in records) continue
//
//        try {
//            val detail = PixivClientPool.free().userDetail(uid = uid).apply { twitter() }
//            if (detail.profile.totalArtwork > detail.user.count()) {
//                logger.info { "ALIAS<${alias}>(${uid})[${detail.user.name}]有${detail.profile.totalArtwork}个作品尝试缓存" }
//                emitAll(getUserIllusts(detail = detail))
//            }
//        } catch (e: Throwable) {
//            logger.warning({ "别名缓存${uid}失败" }, e)
//        }
//    }
//}
//
//internal fun PixivHelper.getRelated(pid: Long, limit: Long = RELATED_LIMIT) = flow {
//    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
//        if (active().not()) return@flow
//        runCatching {
//            illustRelated(pid = pid, offset = offset).illusts
//        }.onSuccess {
//            if (it.isEmpty()) return@flow
//            emit(it)
//            logger.verbose { "加载[${pid}]相关列表第${page}页{${it.size}}成功" }
//        }.onFailure {
//            logger.warning({ "加载[${pid}]相关列表第${page}页失败" }, it)
//        }
//    }
//}
//
//internal fun getNaviRank(year: Year?) = getNaviRank(list = months(year = year))
//
//internal suspend fun getArticle(article: SpotlightArticle) = getListIllusts(
//    info = Pixivision.getArticle(aid = article.aid).illusts
//)
//
//internal suspend fun PixivHelper.randomArticles(limit: Long = ARTICLE_LIMIT): List<SpotlightArticle> {
//    val random = (0..limit).random()
//    return spotlightArticles(category = CategoryType.ILLUST, offset = random).articles.ifEmpty {
//        randomArticles(limit = random - 1)
//    }
//}
//
//internal fun PixivHelper.getWalkThrough(times: Int = 1) = flow {
//    for (page in 0 until times) {
//        if (active().not()) break
//        runCatching {
//            illustWalkThrough().illusts
//        }.onSuccess { list ->
//            emit(list)
//            logger.verbose { "加载第${page}次WalkThrough成功" }
//        }.onFailure {
//            logger.warning({ "加载第${page}次WalkThrough失败" }, it)
//        }
//    }
//}
//
//internal fun PixivHelper.getSearchUser(name: String, limit: Long = SEARCH_LIMIT) = flow {
//    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
//        if (active().not()) return@flow
//        runCatching {
//            searchUser(word = name, offset = offset).previews
//        }.onSuccess {
//            emit(it)
//            logger.verbose { "加载搜索用户(${name})第${page}页{${it.size}}成功" }
//        }.onFailure {
//            logger.warning({ "加载搜索用户(${name})第${page}页失败" }, it)
//        }
//    }
//}
//
//internal suspend fun PixivHelper.loadWeb(url: Url, regex: Regex): Set<Long> {
//    val text: String = useHttpClient { it.get(url) }
//    val result = regex.findAll(text)
//    return result.mapTo(HashSet()) { it.value.toLong() }
//}
//
//internal fun PixivHelper.getTrending(times: Int = 1) = flow {
//    for (page in 0 until times) {
//        if (active().not()) break
//        runCatching {
//            trendingTagsIllust().trends
//        }.onSuccess { list ->
//            emit(list)
//            logger.verbose { "加载第${page}次WalkThrough成功" }
//        }.onFailure {
//            logger.warning({ "加载第${page}次WalkThrough失败" }, it)
//        }
//    }
//}