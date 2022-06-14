package xyz.cssxsh.mirai.pixiv.task

import kotlinx.coroutines.flow.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.time.LocalDate

public class PixivCacheTask(public val name: String, public var flow: Flow<List<IllustInfo>> = emptyFlow()) {
    public var helper: PixivHelper? = null

    public fun PixivHelper.follow(
        limit: Long = FOLLOW_LIMIT
    ): Flow<List<IllustInfo>> = flow {
        (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
            runCatching {
                client.illustFollow(offset = offset).illusts
            }.onSuccess {
                if (it.isEmpty()) return@flow
                emit(it)
                logger.verbose { "加载用户(${uid})关注用户作品时间线第${page}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载用户(${uid})关注用户作品时间线第${page}页失败" }, it)
            }
        }
    }

    public fun PixivHelper.rank(
        mode: RankMode,
        date: LocalDate? = null,
        limit: Long = LOAD_LIMIT
    ): Flow<List<IllustInfo>> = flow {
        (0 until limit step PAGE_SIZE).forEachIndexed { page, offset ->
            runCatching {
                client.illustRanking(mode = mode, date = date, offset = offset).illusts
            }.onSuccess {
                if (it.isEmpty()) return@flow
                emit(it)
                xyz.cssxsh.mirai.pixiv.logger.verbose { "加载排行榜[${mode}](${date ?: "new"})第${page}页{${it.size}}成功" }
            }.onFailure {
                xyz.cssxsh.mirai.pixiv.logger.warning({ "加载排行榜[${mode}](${date ?: "new"})第${page}页失败" }, it)
            }
        }
    }
}