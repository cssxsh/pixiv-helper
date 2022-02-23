package xyz.cssxsh.mirai.plugin

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.exception.*
import java.time.*

typealias LoadTask = suspend PixivHelper.(String) -> Flow<Collection<IllustInfo>>

private suspend fun active() = currentCoroutineContext().isActive

internal fun Flow<Collection<IllustInfo>>.notCached() = map { list -> list.filterNot { it.pid in ArtWorkInfo } }

internal fun Flow<Collection<IllustInfo>>.eros(mark: Boolean = true) = map { list -> list.filter { it.isEro(mark) } }

internal fun Flow<Collection<IllustInfo>>.isToday(now: LocalDate = LocalDate.now()) = map { list ->
    list.filter { it.createAt.toLocalDate() == now }
}

internal fun Flow<Collection<IllustInfo>>.notHistory(task: String) = map { list ->
    list.filterNot { (task to it.pid) in StatisticTaskInfo }
}

internal fun List<NaviRankRecord>.cached() = ArtWorkInfo.list(map { it.pid })

internal fun UserPreview.isLoaded(): Boolean = illusts.all { it.pid in ArtWorkInfo }

internal fun getRank(mode: RankMode, date: LocalDate? = null, limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active().not()) return@flow
        PixivAuthClient().runCatching {
            illustRanking(mode = mode, date = date, offset = offset).illusts
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it)
            logger.verbose { "加载排行榜[${mode}](${date ?: "new"})第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载排行榜[${mode}](${date ?: "new"})第${page}页失败" }, it)
        }
    }
}

internal fun PixivHelper.getFollowIllusts(limit: Long = FOLLOW_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active().not()) return@flow
        runCatching {
            illustFollow(offset = offset).illusts
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it)
            logger.verbose { "加载用户(${info().user.uid})关注用户作品时间线第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${info().user.uid})关注用户作品时间线第${page}页失败" }, it)
        }
    }
}

internal fun PixivHelper.getRecommended(limit: Long = RECOMMENDED_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active().not()) return@flow
        runCatching {
            illustRecommended(offset = offset).let { it.illusts + it.rankingIllusts }.distinctBy { it.pid }
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it)
            logger.verbose { "加载用户(${info().user.uid})推荐作品第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${info().user.uid})推荐作品第${page}页失败" }, it)
        }
    }
}

internal fun getBookmarks(uid: Long, tag: String? = null, limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).fold<Long, String?>(initial = USER_BOOKMARKS_ILLUST) { url, _ ->
        if (active().not() || url == null) return@flow
        PixivAuthClient().runCatching {
            userBookmarksIllust(uid = uid, tag = tag, url = url)
        }.onSuccess { (list, _) ->
            emit(list)
            logger.verbose { "加载用户(${uid})收藏页{${list.size}} ${url}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${uid})收藏页${url}失败" }, it)
        }.getOrNull()?.nextUrl
    }
}

internal suspend fun getBookmarksRandom(detail: UserDetail, tag: String? = null): IllustData {
    val max = (0..detail.profile.totalIllustBookmarksPublic).random() + PAGE_SIZE
    return PixivAuthClient().userBookmarksIllust(uid = detail.user.id, tag = tag, max = max).apply {
        check(illusts.isEmpty()) { "随机收藏USER[${detail.user.id}]<${tag}>失败" }
    }
}

internal fun getUserIllusts(detail: UserDetail, limit: Long? = null) = flow {
    (0 until (limit ?: detail.profile.totalArtwork) step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active().not()) return@flow
        PixivAuthClient().runCatching {
            userIllusts(uid = detail.user.id, offset = offset).illusts
        }.onSuccess {
            emit(it)
            logger.verbose { "加载用户(${detail.user.id})作品第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${detail.user.id})作品第${page}页失败" }, it)
        }
    }
}

internal fun getUserFollowingPreview(detail: UserDetail, limit: Long? = null) = flow {
    (0 until (limit ?: detail.profile.totalFollowUsers) step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active().not()) return@flow
        PixivAuthClient().runCatching {
            userFollowing(uid = detail.user.id, offset = offset).previews
        }.onSuccess {
            emit(it)
            logger.verbose { "加载用户(${detail.user.id})关注用户第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${detail.user.id})关注用户第${page}页失败" }, it)
        }
    }
}

internal fun getUserFollowing(detail: UserDetail, flush: Boolean) = flow {
    logger.verbose { "关注中共${detail.profile.totalFollowUsers}个画师需要缓存" }
    var index = 0
    getUserFollowingPreview(detail = detail).collect { list ->
        for (preview in list) {
            index++
            if (active().not()) break
            if (Twitter[preview.user.id].isEmpty() || preview.isLoaded().not() || flush) {
                PixivAuthClient().runCatching {
                    val author = userDetail(uid = preview.user.id).apply { twitter() }
                    val total = author.profile.totalArtwork
                    val count = author.user.count()
                    if (total - count > preview.illusts.size || flush) {
                        logger.info { "${index}.USER(${author.user.id})[${total}]尝试缓存" }
                        emitAll(getUserIllusts(detail = author))
                    } else {
                        logger.info { "${index}.USER(${author.user.id})[${total}]有${preview.illusts.size}个作品尝试缓存" }
                        emit(preview.illusts)
                    }
                }.onFailure {
                    logger.warning { "${index}.USER(${preview.user.id})加载失败 $it" }
                }
            }
        }
    }
}

internal fun getUserFollowingMark(detail: UserDetail, jump: Int = 0) = flow {
    logger.verbose { "关注中共${detail.profile.totalFollowUsers}个画师收藏需要缓存" }
    var index = 0
    getUserFollowingPreview(detail = detail).collect { previews ->
        for (preview in previews) {
            index++
            if (active().not()) break
            if (index > jump) {
                runCatching {
                    getBookmarks(uid = preview.user.id).fold(0) { count, it ->
                        emit(it)
                        count + it.size
                    }
                }.onSuccess { total ->
                    logger.info { "${index}.USER(${preview.user.id})[${total}]加载成功" }
                }.onFailure {
                    logger.warning { "${index}.USER(${preview.user.id})加载失败 $it" }
                }
            }
        }
    }
}

internal fun PixivHelper.getBookmarkTagInfos(limit: Long = BOOKMARK_TAG_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active().not()) return@flow
        runCatching {
            userBookmarksTagsIllust(offset = offset).tags
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it)
            logger.verbose { "加载收藏标签第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载收藏标签第${page}页失败" }, it)
        }
    }
}

internal val DELETE_REGEX = """該当作品は削除されたか|作品已删除或者被限制|该作品已被删除，或作品ID不存在。""".toRegex()

internal fun getListIllusts(set: Set<Long>, flush: Boolean = false) = flow {
    val list = mutableListOf<IllustInfo>()
    for (pid in set) {
        if (active().not()) break
        try {
            list.add(PixivAuthClient().getIllustInfo(pid = pid, flush = flush))
        } catch (cause: AppApiException) {
            if (DELETE_REGEX in cause.message) {
                ArtWorkInfo(pid = pid, caption = cause.message).replicate()
            } else {
                logger.warning({ "加载作品($pid)失败" }, cause)
            }
        } catch (cause: RestrictException) {
            cause.illust.toArtWorkInfo().copy(pid = pid, caption = cause.message).replicate()
        }

        if (list.size > PAGE_SIZE) emit(list)
    }
    if (list.isNotEmpty()) emit(list)
}

internal fun getListIllusts(info: Collection<SimpleArtworkInfo>, check: Boolean = true) = flow {
    val list = mutableListOf<IllustInfo>()
    for (item in info) {
        if (active().not()) break
        if (check && item.pid in ArtWorkInfo) continue
        try {
            list.add(PixivAuthClient().getIllustInfo(pid = item.pid, flush = true))
        } catch (cause: AppApiException) {
            if (DELETE_REGEX in cause.message) {
                item.toArtWorkInfo(caption = cause.message).replicate()
            } else {
                logger.warning({ "加载作品信息($item)失败" }, cause)
            }
        } catch (cause: RestrictException) {
            cause.illust.toArtWorkInfo(author = UserBaseInfo(uid = item.uid, name = item.name))
                .copy(pid = item.pid, caption = cause.message).replicate()
        }

        if (list.size > PAGE_SIZE) emit(list)
    }
    if (list.isNotEmpty()) emit(list)
}

internal fun getAliasUserIllusts(list: Collection<AliasSetting>) = flow {
    val records = HashSet<Long>()
    for ((alias, uid) in list) {
        if (active().not()) break
        if (uid in records) continue

        try {
            val detail = PixivAuthClient().userDetail(uid = uid).apply { twitter() }
            if (detail.profile.totalArtwork > detail.user.count()) {
                logger.info { "ALIAS<${alias}>(${uid})[${detail.user.name}]有${detail.profile.totalArtwork}个作品尝试缓存" }
                emitAll(getUserIllusts(detail = detail))
            }
        } catch (e: Throwable) {
            logger.warning({ "别名缓存${uid}失败" }, e)
        }
    }
}

internal fun PixivHelper.getSearchTag(tag: String, limit: Long = SEARCH_LIMIT) = flow {
    val word = tag.split(delimiters = TAG_DELIMITERS).joinToString(" ")
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active().not()) return@flow
        runCatching {
            searchIllust(word = word, offset = offset).illusts
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it)
            logger.verbose { "加载'${tag}'搜索列表第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载'${tag}'搜索列表第${page}页失败" }, it)
        }
    }
}

internal fun PixivHelper.getRelated(pid: Long, limit: Long = RELATED_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active().not()) return@flow
        runCatching {
            illustRelated(pid = pid, offset = offset).illusts
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it)
            logger.verbose { "加载[${pid}]相关列表第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载[${pid}]相关列表第${page}页失败" }, it)
        }
    }
}

private fun months(year: Year?) = buildList {
    var temp = year?.atMonth(1) ?: NaviRank.START
    val limit = minOf(year?.atMonth(12) ?: YearMonth.now(), YearMonth.now())
    while (temp <= limit) {
        add(temp)
        temp = temp.plusMonths(1)
    }
}

internal fun getNaviRank(list: List<YearMonth>) = flow {
    for (month in list) {
        if (active().not()) break
        NaviRank.runCatching {
            (getAllRank(month = month).records + getOverRank(month = month).records.values.flatten())
                .distinctBy { it.pid }
        }.onSuccess {
            logger.verbose { "加载 NaviRank[$month]{${it.size}}成功" }
            emitAll(getListIllusts(info = it))
        }.onFailure {
            logger.warning({ "加载 NaviRank[$month]失败" }, it)
        }
    }
}

internal fun getNaviRank(year: Year?) = getNaviRank(list = months(year = year))

internal suspend fun getArticle(article: SpotlightArticle) = getListIllusts(
    info = Pixivision.getArticle(aid = article.aid).illusts
)

internal suspend fun PixivHelper.randomArticles(limit: Long = ARTICLE_LIMIT): List<SpotlightArticle> {
    val random = (0..limit).random()
    return spotlightArticles(category = CategoryType.ILLUST, offset = random).articles.ifEmpty {
        randomArticles(limit = random - 1)
    }
}

internal fun PixivHelper.getWalkThrough(times: Int = 1) = flow {
    for (page in 0 until times) {
        if (active().not()) break
        runCatching {
            illustWalkThrough().illusts
        }.onSuccess { list ->
            emit(list)
            logger.verbose { "加载第${page}次WalkThrough成功" }
        }.onFailure {
            logger.warning({ "加载第${page}次WalkThrough失败" }, it)
        }
    }
}

internal fun PixivHelper.getSearchUser(name: String, limit: Long = SEARCH_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active().not()) return@flow
        runCatching {
            searchUser(word = name, offset = offset).previews
        }.onSuccess {
            emit(it)
            logger.verbose { "加载搜索用户(${name})第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载搜索用户(${name})第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.loadWeb(url: Url, regex: Regex): Set<Long> {
    val text: String = useHttpClient { it.get(url) }
    val result = regex.findAll(text)
    return result.mapTo(HashSet()) { it.value.toLong() }
}

internal fun PixivHelper.getTrending(times: Int = 1) = flow {
    for (page in 0 until times) {
        if (active().not()) break
        runCatching {
            trendingTagsIllust().trends
        }.onSuccess { list ->
            emit(list)
            logger.verbose { "加载第${page}次WalkThrough成功" }
        }.onFailure {
            logger.warning({ "加载第${page}次WalkThrough失败" }, it)
        }
    }
}

internal fun getCacheUser(records: List<StatisticUserInfo>) = flow {
    logger.info { "CacheUser有${records.size}个用户尝试缓存" }

    for ((index, record) in records.withIndex()) {
        if (active().not()) break
        try {
            val author = PixivAuthClient().userDetail(uid = record.uid).apply { twitter() }
            val total = author.profile.totalArtwork
            if (total > record.count) {
                logger.info { "${index}.USER(${author.user.id})[${author.user.name}]有${total}个作品尝试缓存" }
                emitAll(getUserIllusts(detail = author))
            }
        } catch (cause: Throwable) {
            logger.warning({ "${index}.${record}加载失败" }, cause)
        }
    }
}