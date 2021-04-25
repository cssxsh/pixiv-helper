package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.time.LocalDate

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

internal suspend fun PixivHelper.getRank(mode: RankMode, date: LocalDate? = null, limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
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
        if (isActive) runCatching {
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
        if (isActive) runCatching {
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
            if (isActive.not() || url == null) return@flow
            userBookmarksIllust(uid = uid, tag = tag, url = url)
        }.onSuccess { (list, _) ->
            emit(list)
            logger.verbose { "加载用户(${uid})收藏页{${list.size}} ${url}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${uid})收藏页${url}失败" }, it)
        }.getOrNull()?.nextUrl
    }
}

internal suspend fun PixivHelper.getUserIllusts(detail: UserDetail, limit: Long? = null) = flow {
    (0 until (limit ?: detail.total()) step PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
            userIllusts(uid = detail.user.id, offset = offset).illusts
        }.onSuccess {
            emit(it)
            logger.verbose { "加载用户(${detail.user.id})作品第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${detail.user.id})作品第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getUserIllusts(uid: Long, limit: Long? = null) =
    getUserIllusts(detail = userDetail(uid = uid), limit = limit)

internal suspend fun PixivHelper.getUserFollowingPreview(detail: UserDetail, limit: Long? = null) = flow {
    (0 until (limit ?: detail.profile.totalFollowUsers) step PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
            userFollowing(uid = detail.user.id, offset = offset).previews
        }.onSuccess {
            emit(it)
            logger.verbose { "加载用户(${detail.user.id})关注用户第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${detail.user.id})关注用户第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getListIllusts(set: Set<Long>) = flow {
    useMappers { mappers ->
        set.filterNot { mappers.artwork.contains(it) }
    }.chunked(PAGE_SIZE.toInt()).forEach { list ->
        if (isActive) list.mapNotNull { pid ->
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
        }.let { emit(it) }
    }
}

internal suspend fun PixivHelper.getListIllusts(results: List<SearchResult>) = flow {
    useMappers { mappers ->
        results.filterNot { mappers.artwork.contains(it.pid) }
    }.chunked(PAGE_SIZE.toInt()).forEach { list ->
        if (isActive) list.mapNotNull { result ->
            runCatching {
                getIllustInfo(pid = result.pid, flush = true).apply {
                    check(user.id != 0L) { "作品已删除或者被限制, Redirect: ${getOriginImageUrls().single()}" }
                }
            }.onFailure {
                if (it.isNotCancellationException()) {
                    logger.warning({ "加载搜索结果($result)失败" }, it)
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
        }.let { emit(it) }
    }
}

internal suspend fun PixivHelper.searchTag(tag: String, limit: Long = SEARCH_LIMIT) = flow {
    (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
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
        if (isActive) runCatching {
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