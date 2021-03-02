package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.isActive
import net.mamoe.mirai.utils.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.api.apps.*
import xyz.cssxsh.pixiv.data.apps.*
import java.time.LocalDate

private const val LOAD_LIMIT = 3_000L

internal fun List<IllustInfo>.nocache() = useArtWorkInfoMapper { mapper ->
    filter { mapper.contains(it.pid).not() }
}

internal fun List<IllustInfo>.nomanga() = filter { it.type != WorkContentType.MANGA }

internal suspend fun PixivHelper.getRank(mode: RankMode, date: LocalDate?, limit: Long = LOAD_LIMIT) = buildList {
    (0 until limit step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
            illustRanking(mode = mode, date = date, offset = offset).illusts
        }.onSuccess {
            if (it.isEmpty()) return@buildList
            addAll(it)
            PixivHelperPlugin.logger.verbose { "加载排行榜[${mode}](${date ?: "new"})第${page}页{${it.size}}成功" }
        }.onFailure {
            PixivHelperPlugin.logger.warning({ "加载排行榜[${mode}](${date ?: "new"})第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getFollowIllusts(limit: Long = LOAD_LIMIT) = buildList {
    (0 until limit step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
            illustFollow(offset = offset).illusts
        }.onSuccess {
            if (it.isEmpty()) return@buildList
            addAll(it)
            PixivHelperPlugin.logger.verbose { "加载用户(${getAuthInfo().user.uid})关注用户作品时间线第${page}页{${it.size}}成功" }
        }.onFailure {
            PixivHelperPlugin.logger.warning({ "加载用户(${getAuthInfo().user.uid})关注用户作品时间线第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getRecommended(limit: Long = LOAD_LIMIT) = buildList {
    (0 until limit step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
            userRecommended(offset = offset).userPreviews
        }.onSuccess {
            if (it.isEmpty()) return@buildList
            addAll(it)
            PixivHelperPlugin.logger.verbose { "加载用户(${getAuthInfo().user.uid})推荐用户预览第${page}页{${it.size}}成功" }
        }.onFailure {
            PixivHelperPlugin.logger.warning({ "加载用户(${getAuthInfo().user.uid})推荐用户预览第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getBookmarks(uid: Long, limit: Long = LOAD_LIMIT) = buildList {
    (0 until limit step AppApi.PAGE_SIZE).fold<Long, String?>(AppApi.USER_BOOKMARKS_ILLUST) { url, _ ->
        runCatching {
            if (isActive.not() || url == null) return@buildList
            userBookmarksIllust(uid = uid, url = url)
        }.onSuccess { (list, _) ->
            addAll(list)
            PixivHelperPlugin.logger.verbose { "加载用户(${uid})收藏页{${list.size}} ${url}成功" }
        }.onFailure {
            PixivHelperPlugin.logger.warning({ "加载用户(${uid})收藏页${url}失败" }, it)
        }.getOrNull()?.nextUrl
    }
}

internal suspend fun PixivHelper.getUserIllusts(detail: UserDetail, limit: Long? = null) = buildList {
    (0 until (limit ?: detail.total()) step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
            userIllusts(uid = detail.user.id, offset = offset).illusts
        }.onSuccess {
            addAll(it)
            PixivHelperPlugin.logger.verbose { "加载用户(${detail.user.id})作品第${page}页{${it.size}}成功" }
        }.onFailure {
            PixivHelperPlugin.logger.warning({ "加载用户(${detail.user.id})作品第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getUserIllusts(uid: Long, limit: Long? = null) = getUserIllusts(userDetail(uid = uid), limit = limit)

internal suspend fun PixivHelper.getUserFollowingPreview(detail: UserDetail) = buildList {
    (0 until detail.profile.totalFollowUsers step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
        if (isActive) runCatching {
            userFollowing(uid = detail.user.id, offset = offset).userPreviews
        }.onSuccess {
            addAll(it)
            PixivHelperPlugin.logger.verbose { "加载用户(${detail.user.id})关注用户第${page}页{${it.size}}成功" }
        }.onFailure {
            PixivHelperPlugin.logger.warning({ "加载用户(${detail.user.id})关注用户第${page}页失败" }, it)
        }
    }
}

internal suspend fun PixivHelper.getUserFollowingPreview() = getUserFollowingPreview(userDetail(uid = getAuthInfo().user.uid))