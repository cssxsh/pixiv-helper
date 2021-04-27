package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.io.File
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

internal fun Flow<List<IllustInfo>>.notCached() = map { list ->
    useMappers { mappers ->
        list.filterNot { mappers.artwork.contains(it.pid) }
    }
}

internal fun Flow<List<IllustInfo>>.types(type: WorkContentType) = map { list ->
    list.filter { it.type == type }
}

internal fun Flow<List<IllustInfo>>.eros() = map { list ->
    list.filter { it.isEro() }
}

internal fun List<NaviRankRecord>.cached() = useMappers { mappers ->
    mapNotNull { record ->
        mappers.artwork.findByPid(record.pid)
    }
}

internal suspend fun PixivHelper.getRank(mode: RankMode, date: LocalDate? = null, limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (currentCoroutineContext().isActive) runCatching {
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
        if (currentCoroutineContext().isActive) runCatching {
            illustFollow(offset = offset).illusts
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it)
            logger.verbose { "加载用户(${getAuthInfo().user.uid})关注用户作品时间线第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${getAuthInfo().user.uid})关注用户作品时间线第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getRecommended(limit: Long = RECOMMENDED_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (currentCoroutineContext().isActive) runCatching {
            illustRecommended(offset = offset).let { it.illusts + it.rankingIllusts }
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it)
            logger.verbose { "加载用户(${getAuthInfo().user.uid})推荐作品第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${getAuthInfo().user.uid})推荐作品第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getBookmarks(uid: Long, tag: String? = null, limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).fold<Long, String?>(initial = USER_BOOKMARKS_ILLUST) { url, _ ->
        runCatching {
            if (currentCoroutineContext().isActive.not() || url == null) return@flow
            userBookmarksIllust(uid = uid, tag = tag, url = url)
        }.onSuccess { (list, _) ->
            emit(list)
            logger.verbose { "加载用户(${uid})收藏页{${list.size}} ${url}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${uid})收藏页${url}失败" }, it)
        }.getOrNull()?.nextUrl
    }
}

private const val PID_MAX = 999_999_999L

internal suspend fun PixivHelper.bookmarksRandom(uid: Long, tag: String? = null, limit: Long = PID_MAX): IllustData {
    val random = (0..limit).random()
    return userBookmarksIllust(uid = uid, tag = tag).takeIf {
        it.illusts.isNotEmpty()
    } ?: bookmarksRandom(uid = uid, tag = tag, limit = random + 1)
}

internal suspend fun PixivHelper.getUserIllusts(detail: UserDetail, limit: Long? = null) = flow {
    (0 until (limit ?: detail.total()) step PAGE_SIZE).forEachIndexed { page, offset ->
        if (currentCoroutineContext().isActive) runCatching {
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
        if (currentCoroutineContext().isActive) runCatching {
            userFollowing(uid = detail.user.id, offset = offset).previews
        }.onSuccess {
            emit(it)
            logger.verbose { "加载用户(${detail.user.id})关注用户第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${detail.user.id})关注用户第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getUserFollowing(detail: UserDetail, limit: Long? = null): Flow<List<IllustInfo>> {
    logger.verbose { "关注中共${detail.profile.totalFollowUsers}个画师需要缓存" }
    var index = 0
    return getUserFollowingPreview(detail = detail, limit = limit).transform { list ->
        list.forEach { preview ->
            index++
            if (currentCoroutineContext().isActive && (preview.isLoaded().not() || preview.user.count() < PAGE_SIZE)) {
                runCatching {
                    val author = userDetail(uid = preview.user.id)
                    if (author.total() > author.user.count() + preview.illusts.size) {
                        logger.verbose { "${index}.USER(${author.user.id})有${author.total()}个作品尝试缓存" }
                        emitAll(getUserIllusts(author))
                    } else {
                        logger.verbose { "${index}.USER(${author.user.id})有${preview.illusts.size}个作品尝试缓存" }
                        emit(preview.illusts)
                    }
                }
            }
        }
    }
}

internal suspend fun PixivHelper.getBookmarkTagInfos(limit: Long = BOOKMARK_TAG_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (currentCoroutineContext().isActive) runCatching {
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

internal suspend fun PixivHelper.getListIllusts(set: Set<Long>) = flow {
    useMappers { mappers ->
        set.filterNot { mappers.artwork.contains(it) }
    }.chunked(PAGE_SIZE.toInt()).forEach { list ->
        if (currentCoroutineContext().isActive) list.mapNotNull { pid ->
            runCatching {
                getIllustInfo(pid = pid, flush = true).apply {
                    check(user.id != 0L) { "作品已删除或者被限制, Redirect: ${getOriginImageUrls().single()}" }
                }
            }.onFailure {
                if (it.isNotCancellationException()) {
                    logger.warning({ "加载作品($pid)失败" }, it)
                }
                if (it.message == "該当作品は削除されたか、存在しない作品IDです。" || it.message.orEmpty().contains("作品已删除或者被限制")) {
                    useMappers { mappers ->
                        mappers.artwork.replaceArtWork(ArtWorkInfo(
                            pid = pid,
                            uid = 0,
                            title = "",
                            caption = it.message.orEmpty(),
                            createAt = 0,
                            pageCount = 0,
                            sanityLevel = 7,
                            type = 0,
                            width = 0,
                            height = 0,
                            totalBookmarks = 0,
                            totalComments = 0,
                            totalView = 0,
                            age = 0,
                            isEro = false,
                            deleted = true,
                        ))
                    }
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
    useMappers { mappers ->
        info.filterNot { mappers.artwork.contains(it.pid) }
    }.chunked(PAGE_SIZE.toInt()).forEach { list ->
        if (currentCoroutineContext().isActive) list.mapNotNull { result ->
            runCatching {
                getIllustInfo(pid = result.pid, flush = true).apply {
                    check(user.id != 0L) { "作品已删除或者被限制, Redirect: ${getOriginImageUrls().single()}" }
                }
            }.onFailure {
                if (it.isNotCancellationException()) {
                    logger.warning({ "加载作品信息($result)失败" }, it)
                }
                if (it.message == "該当作品は削除されたか、存在しない作品IDです。" || it.message.orEmpty().contains("作品已删除或者被限制")) {
                    useMappers { mappers ->
                        if (mappers.user.findByUid(result.uid) == null) {
                            mappers.user.replaceUser(UserBaseInfo(
                                uid = result.uid,
                                name = result.name,
                                account = ""
                            ))
                        }
                        mappers.artwork.replaceArtWork(ArtWorkInfo(
                            pid = result.pid,
                            uid = result.uid,
                            title = result.title,
                            caption = it.message.orEmpty(),
                            createAt = 0,
                            pageCount = 0,
                            sanityLevel = 7,
                            type = 0,
                            width = 0,
                            height = 0,
                            totalBookmarks = 0,
                            totalComments = 0,
                            totalView = 0,
                            age = 0,
                            isEro = false,
                            deleted = true,
                        ))
                    }
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
    useMappers { it.statistic.alias() }.map { it.uid }.toSet().sorted().also { set ->
        logger.verbose { "别名中{${set.first()..set.last()}}共${list.size}个画师需要缓存" }
        set.forEachIndexed { index, uid ->
            if (currentCoroutineContext().isActive) runCatching {
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
        if (currentCoroutineContext().isActive) runCatching {
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
        if (currentCoroutineContext().isActive) runCatching {
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

internal suspend fun PixivHelper.getNaviRank(year: Year?) = flow {
    months(year = year).forEach { month ->
        if (currentCoroutineContext().isActive) NaviRank.runCatching {
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

internal suspend fun PixivHelper.getArticle(article: SpotlightArticle) = getListIllusts(
    info = Pixivision.getArticle(aid = article.aid).illusts
)

internal suspend fun PixivHelper.articlesRandom(limit: Long = ARTICLE_LIMIT): SpotlightArticleData {
    val random = (0..limit).random()
    return spotlightArticles(category = CategoryType.ILLUST, offset = random).takeIf {
        it.articles.isNotEmpty()
    } ?: articlesRandom(limit = random - 1)
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
            if (currentCoroutineContext().isActive) second.listDirs(range).orEmpty().mapNotNull { dir ->
                dir.listFiles().orEmpty().size
                dir.resolve("${dir.name}.json").takeIf { file ->
                    useMappers { it.artwork.contains(dir.name.toLong()) } && file.canRead()
                }?.readIllustInfo()
            }.let {
                if (it.isNotEmpty()) {
                    emit(it)
                }
            }
        }
    }
}