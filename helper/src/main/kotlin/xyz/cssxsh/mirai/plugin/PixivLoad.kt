package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.api.apps.*
import xyz.cssxsh.pixiv.data.apps.*
import java.time.LocalDate

private const val LOAD_LIMIT = 3_000L

internal fun List<IllustInfo>.nocache() = useArtWorkInfoMapper { mapper ->
    filter { mapper.contains(it.pid).not() }
}

internal fun List<IllustInfo>.nomanga() = filter { it.type != WorkContentType.MANGA }

internal fun List<IllustInfo>.noero() = filter { it.isEro().not() }

internal suspend fun PixivHelper.getRank(mode: RankMode, date: LocalDate?, limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
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

internal suspend fun PixivHelper.getFollowIllusts(limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
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

internal suspend fun PixivHelper.getRecommended(limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
            userRecommended(offset = offset).userPreviews
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it)
            logger.verbose { "加载用户(${getAuthInfo().user.uid})推荐用户预览第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${getAuthInfo().user.uid})推荐用户预览第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getBookmarks(uid: Long, limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step AppApi.PAGE_SIZE).fold<Long, String?>(AppApi.USER_BOOKMARKS_ILLUST) { url, _ ->
        runCatching {
            if (isActive.not() || url == null) return@flow
            userBookmarksIllust(uid = uid, url = url)
        }.onSuccess { (list, _) ->
            emit(list)
            logger.verbose { "加载用户(${uid})收藏页{${list.size}} ${url}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${uid})收藏页${url}失败" }, it)
        }.getOrNull()?.nextUrl
    }
}

internal suspend fun PixivHelper.getUserIllusts(detail: UserDetail, limit: Long? = null) = flow {
    (0 until (limit ?: detail.total()) step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
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

internal suspend fun PixivHelper.getUserIllusts(uid: Long, limit: Long? = null) = getUserIllusts(userDetail(uid = uid), limit = limit)

internal suspend fun PixivHelper.getUserFollowingPreview(detail: UserDetail) = flow {
    (0 until detail.profile.totalFollowUsers step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
            userFollowing(uid = detail.user.id, offset = offset).userPreviews
        }.onSuccess {
            emit(it)
            logger.verbose { "加载用户(${detail.user.id})关注用户第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载用户(${detail.user.id})关注用户第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getListIllusts(set: Set<Long>) = set.chunked(AppApi.PAGE_SIZE.toInt()).asFlow().map { list ->
    list.mapNotNull { pid ->
        runCatching {
            getIllustInfo(pid = pid, flush = true).apply {
                check(user.id != 0L) { "作品已删除或者被限制, Redirect: ${getOriginImageUrls().single()}" }
            }
        }.onFailure {
            if (it.isNotCancellationException()) {
                logger.warning({ "加载作品($pid)失败" }, it)
            }
        }.getOrNull()
    }
}

internal suspend fun PixivHelper.searchTag(tag: String, limit: Long = LOAD_LIMIT) = flow {
    (0 until limit step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
            searchIllust(word = tag, offset = offset).illusts
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it.noero())
            logger.verbose { "加载'${tag}'搜索列表第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载'${tag}'搜索列表第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getRelated(pid: Long, illusts: List<Long>) = flow {
    (0 until AppApi.RELATED_OFFSET step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
        runCatching {
            illustRelated(pid = pid, seedIllustIds = illusts, offset = offset).illusts
        }.onSuccess {
            if (it.isEmpty()) return@flow
            emit(it.noero())
            logger.verbose { "加载[${pid}]相关列表第${page}页{${it.size}}成功" }
        }.onFailure {
            logger.warning({ "加载[${pid}]相关列表第${page}页失败" }, it)
        }
    }
}