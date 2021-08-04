package xyz.cssxsh.mirai.plugin

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.io.*
import java.time.*

typealias LoadTask = suspend PixivHelper.() -> Flow<Collection<IllustInfo>>

private suspend fun active() = currentCoroutineContext().isActive

internal fun UserDetail.total() = profile.totalIllusts + profile.totalManga

internal fun Flow<Collection<IllustInfo>>.notCached() = map { list -> list.filterNot { it.pid in ArtWorkInfo } }

internal fun Flow<Collection<IllustInfo>>.types(type: WorkContentType) = map { list -> list.filter { it.type == type } }

internal fun Flow<Collection<IllustInfo>>.eros() = map { list -> list.filter { it.isEro() } }

internal fun Flow<Collection<IllustInfo>>.isToday() = map { list ->
    val now = LocalDate.now()
    list.filter { it.createAt.toLocalDate() == now }
}

internal fun Flow<Collection<IllustInfo>>.notHistory(task: String) = map { list ->
    list.filterNot { (task to it.pid) in StatisticTaskInfo }
}

internal fun List<NaviRankRecord>.cached() = ArtWorkInfo.list(map { it.pid })

internal suspend fun PixivHelper.getRank(mode: RankMode, date: LocalDate? = null, limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active()) runCatching {
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

internal suspend fun PixivHelper.getFollowIllusts(limit: Long = FOLLOW_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active()) runCatching {
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

internal suspend fun PixivHelper.getRecommended(limit: Long = RECOMMENDED_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active()) runCatching {
            illustRecommended(offset = offset).let { it.illusts + it.rankingIllusts }.associateBy { it.pid }.values
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it)
            logger.verbose { "加载用户(${info().user.uid})推荐作品第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${info().user.uid})推荐作品第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getBookmarks(uid: Long, tag: String? = null, limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).fold<Long, String?>(initial = USER_BOOKMARKS_ILLUST) { url, _ ->
        runCatching {
            if (active().not() || url == null) return@flow
            userBookmarksIllust(uid = uid, tag = tag, url = url)
        }.onSuccess { (list, _) ->
            emit(list)
            logger.verbose { "加载用户(${uid})收藏页{${list.size}} ${url}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${uid})收藏页${url}失败" }, it)
        }.getOrNull()?.nextUrl
    }
}

internal suspend fun PixivHelper.bookmarksRandom(detail: UserDetail, tag: String? = null): IllustData {
    val max = (0..detail.profile.totalIllustBookmarksPublic).random() + PAGE_SIZE
    return userBookmarksIllust(uid = detail.user.id, tag = tag, max = max).apply {
        check(illusts.isEmpty()) { "随机收藏USER[${detail.user.id}]<${tag}>失败" }
    }
}

internal suspend fun PixivHelper.getUserIllusts(detail: UserDetail, limit: Long? = null) = flow {
    (0 until (limit ?: detail.total()) step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active()) runCatching {
            userIllusts(uid = detail.user.id, offset = offset).illusts
        }.onSuccess {
            emit(it)
            logger.verbose { "加载用户(${detail.user.id})作品第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${detail.user.id})作品第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getUserFollowingPreview(detail: UserDetail, limit: Long? = null) = flow {
    (0 until (limit ?: detail.profile.totalFollowUsers) step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active()) runCatching {
            userFollowing(uid = detail.user.id, offset = offset).previews
        }.onSuccess {
            emit(it)
            logger.verbose { "加载用户(${detail.user.id})关注用户第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${detail.user.id})关注用户第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getUserFollowing(detail: UserDetail, flush: Boolean): Flow<List<IllustInfo>> {
    logger.verbose { "关注中共${detail.profile.totalFollowUsers}个画师需要缓存" }
    var index = 0
    return getUserFollowingPreview(detail = detail).transform { list ->
        list.forEach { preview ->
            index++
            val count = preview.user.count()
            val loaded = preview.isLoaded()
            if (active() && (loaded.not() || count < PAGE_SIZE || flush)) {
                runCatching {
                    val author = userDetail(uid = preview.user.id)
                    val total = author.total()
                    if (total - count > preview.illusts.size || flush) {
                        logger.info { "${index}.USER(${author.user.id})[${total}]尝试缓存" }
                        emitAll(getUserIllusts(author))
                    } else {
                        logger.info { "${index}.USER(${author.user.id})[${total}]有${preview.illusts.size}个作品尝试缓存" }
                        emit(preview.illusts)
                    }
                }.onFailure {
                    logger.warning { "${index}.USER(${preview.user.id})加载失败 $it" }
                }
            }
        }
    }.onCompletion {
        send { "共${detail.profile.totalFollowUsers}个画师处理完成" }
    }
}

internal suspend fun PixivHelper.getUserFollowingMark(detail: UserDetail, jump: Int = 0): Flow<List<IllustInfo>> {
    logger.verbose { "关注中共${detail.profile.totalFollowUsers}个画师收藏需要缓存" }
    var index = 0
    return getUserFollowingPreview(detail = detail).transform { list ->
        list.forEach { preview ->
            index++
            if (active() && index > jump) {
                runCatching {
                    var count = 0
                    emitAll(getBookmarks(preview.user.id).onEach { count += it.size })
                    count
                }.onSuccess {
                    logger.info { "${index}.USER(${preview.user.id})[${it}]加载成功" }
                }.onFailure {
                    logger.warning { "${index}.USER(${preview.user.id})加载失败 $it" }
                }
            }
        }
    }.onCompletion {
        send { "共${detail.profile.totalFollowUsers}个画师处理完成" }
    }
}

internal suspend fun PixivHelper.getBookmarkTagInfos(limit: Long = BOOKMARK_TAG_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active()) runCatching {
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

private val DELETE_REGEX = """該当作品は削除されたか|作品已删除或者被限制""".toRegex()

internal suspend fun PixivHelper.getListIllusts(set: Set<Long>, flush: Boolean = false) = flow {
    set.chunked(PAGE_SIZE.toInt()).forEach { list ->
        if (active().not()) return@flow
        list.mapNotNull { pid ->
            runCatching {
                getIllustInfo(pid = pid, flush = flush).check()
            }.onFailure {
                if (it.isNotCancellationException()) {
                    logger.warning({ "加载作品($pid)失败" }, it)
                }
                if (DELETE_REGEX in it.message.orEmpty()) {
                    ArtWorkInfo(pid = pid, caption = it.message.orEmpty()).replicate()
                }
            }.getOrNull()
        }.let {
            if (it.isNotEmpty()) {
                emit(it)
            }
        }
    }
}

internal suspend fun PixivHelper.getListIllusts(info: Collection<SimpleArtworkInfo>) = flow {
    info.filterNot { it.pid in ArtWorkInfo }.chunked(PAGE_SIZE.toInt()).forEach { list ->
        if (active()) list.mapNotNull { result ->
            runCatching {
                getIllustInfo(pid = result.pid, flush = true).apply {
                    check(user.id != 0L) { "该作品已被删除或者被限制, Redirect: ${getOriginImageUrls().single()}" }
                }
            }.onFailure {
                if (it.isNotCancellationException()) {
                    logger.warning({ "加载作品信息($result)失败" }, it)
                }
                if (it.message == "該当作品は削除されたか、存在しない作品IDです。" || it.message.orEmpty().contains("该作品已被删除")) {
                    result.toArtWorkInfo().copy(caption = it.message.orEmpty()).replicate()
                }
            }.getOrNull()
        }.let {
            if (it.isNotEmpty()) {
                emit(it)
            }
        }
    }
}

internal suspend fun PixivHelper.getAliasUserIllusts(list: Collection<AliasSetting>) = flow {
    AliasSetting.all().associateBy { it.uid }.keys.let { set ->
        logger.verbose { "别名中{${set.first()..set.last()}}共${list.size}个画师需要缓存" }
        set.forEachIndexed { index, uid ->
            if (active()) runCatching {
                userDetail(uid = uid).let { detail ->
                    if (detail.total() > detail.user.count()) {
                        logger.verbose { "${index}.USER(${uid})有${detail.total()}个作品尝试缓存" }
                        emitAll(getUserIllusts(detail))
                    }
                }
            }.onFailure {
                logger.warning({ "别名缓存${uid}失败" }, it)
            }
        }
    }
}

internal suspend fun PixivHelper.getSearchTag(tag: String, limit: Long = SEARCH_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active()) runCatching {
            searchIllust(word = tag, offset = offset).illusts
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it)
            logger.verbose { "加载'${tag}'搜索列表第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载'${tag}'搜索列表第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getRelated(pid: Long, seeds: Set<Long>, limit: Long = RELATED_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active()) runCatching {
            illustRelated(pid = pid, seeds = seeds, offset = offset).illusts
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

internal suspend fun PixivHelper.getNaviRank(list: List<YearMonth>) = flow {
    list.forEach { month ->
        if (active()) NaviRank.runCatching {
            (getAllRank(month = month).records + getOverRank(month = month).records.values.flatten()).filter {
                it.type == WorkContentType.ILLUST
            }.toSet()
        }.onSuccess {
            logger.verbose { "加载 NaviRank[$month]{${it.size}}成功" }
            emitAll(getListIllusts(info = it))
        }.onFailure {
            logger.warning({ "加载 NaviRank[$month]失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getNaviRank(year: Year?) = getNaviRank(list = months(year = year))

internal suspend fun PixivHelper.getArticle(article: SpotlightArticle) = getListIllusts(
    info = Pixivision.getArticle(aid = article.aid).illusts
)

internal suspend fun PixivHelper.randomArticles(limit: Long = ARTICLE_LIMIT): SpotlightArticleData {
    val random = (0..limit).random()
    return spotlightArticles(category = CategoryType.ILLUST, offset = random).takeIf {
        it.articles.isNotEmpty()
    } ?: randomArticles(limit = random - 1)
}

internal suspend fun PixivHelper.getWalkThrough(times: Int = 1) = flow {
    (0 until times).forEach { page ->
        if (active()) runCatching {
            illustWalkThrough().illusts
        }.onSuccess { list ->
            list.chunked(PAGE_SIZE.toInt()).forEach {
                emit(it)
            }
            logger.verbose { "加载第${page}次WalkThrough成功" }
        }.onFailure {
            logger.warning({ "加载第${page}次WalkThrough失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getSearchUser(name: String, limit: Long = SEARCH_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (active()) runCatching {
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
    return result.map { it.value.toLong() }.toSet()
}

private fun File.listDirs(range: LongRange) = listFiles { file ->
    file.name.matches("""\d+[_]+""".toRegex()) && file.isDirectory && intersect(file.range(), range)
}

private fun File.range() = name.replace('_', '0').toLong()..name.replace('_', '9').toLong()

private fun intersect(from: LongRange, to: LongRange) = from.first <= to.last && to.first <= from.last

internal fun localCache(range: LongRange) = flow {
    PixivHelperSettings.cacheFolder.also {
        logger.verbose { "从 ${it.absolutePath} 加载作品信息" }
    }.listDirs(range).orEmpty().asFlow().map { first ->
        first.listDirs(range).orEmpty().forEach { second ->
            if (active()) second.listDirs(range).orEmpty().mapNotNull { dir ->
                dir.listFiles().orEmpty().size
                dir.resolve("${dir.name}.json").takeIf { file ->
                    dir.name.toLong() in ArtWorkInfo && file.canRead()
                }?.readIllustInfo()
            }.let {
                if (it.isNotEmpty()) {
                    emit(it)
                }
            }
        }
    }
}