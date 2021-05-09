package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.apps.*

object PixivFollowCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "follow",
    description = "PIXIV关注指令"
) {

    private var PixivHelper.follow  by PixivHelperDelegate { CancelledJob }

    private suspend fun CommandSenderOnMessage<*>.follow(block: suspend PixivHelper.() -> Set<Long>) = withHelper {
        check(!follow.isActive) { "正在关注中, ${follow}..." }
        follow = launch(Dispatchers.IO) {
            block().groupBy { uid ->
                isActive && runCatching {
                    userFollowAdd(uid = uid)
                }.onSuccess {
                    logger.info { "用户(${getAuthInfo().user.uid})添加关注(${uid})成功, $it" }
                }.onFailure {
                    logger.warning({ "用户(${getAuthInfo().user.uid})添加关注(${uid})失败, 将开始延时" }, it)
                }.isSuccess
            }.let { (success, failure) ->
                send {
                    "关注画师完毕, 关注成功数: ${success?.size ?: 0}, 失败数: ${failure?.size ?: 0}"
                }
            }
        }
        null
    }

    private suspend fun PixivHelper.getFollowed(uid: Long): Set<Long> {
        return getUserFollowingPreview(detail = userDetail(uid = uid)).toList().flatten().map { it.user.id }.toSet()
    }

    @SubCommand
    @Description("为当前助手关注指定用户")
    suspend fun CommandSenderOnMessage<*>.user(vararg users: String) = follow { users.map { it.toLong() }.toSet() }

    @SubCommand
    @Description("关注色图缓存中的较好画师")
    suspend fun CommandSenderOnMessage<*>.good() = follow {
        val followed = getFollowed(uid = getAuthInfo().user.uid)
        useMappers { it.artwork.userEroCount() }.mapNotNull { (uid, count) ->
            if (count > PixivHelperSettings.eroInterval) uid else null
        }.let {
            logger.verbose { "共统计了${it.size}名画师" }
            it - followed
        }.sorted().also {
            logger.info { "用户(${getAuthInfo().user.uid})已关注${followed.size}, 共有${it.size}个用户等待关注" }
            send {
                "{${it.first()..it.last()}}共${it.size}个画师等待关注"
            }
        }.toSet()
    }

    @SubCommand
    @Description("关注指定用户的关注")
    suspend fun CommandSenderOnMessage<*>.copy(uid: Long) = follow { getFollowed(uid = uid) }
}