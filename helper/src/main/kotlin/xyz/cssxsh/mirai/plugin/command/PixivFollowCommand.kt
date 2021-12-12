package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.pixiv.apps.*

object PixivFollowCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "follow",
    description = "PIXIV关注指令"
), PixivHelperCommand {

    private var PixivHelper.follow by PixivHelperDelegate { CompletedJob }

    private suspend fun UserCommandSender.follow(block: suspend PixivHelper.() -> Set<Long>) = withHelper {
        check(!follow.isActive) { "正在关注中, ${follow}..." }
        follow = launch(Dispatchers.IO) {
            val (success, failure) = block().groupBy { uid ->
                isActive && runCatching {
                    userFollowAdd(uid = uid)
                }.onSuccess {
                    logger.info { "用户(${info().user.uid})添加关注(${uid})成功, $it" }
                }.onFailure {
                    logger.warning({ "用户(${info().user.uid})添加关注(${uid})失败, 将开始延时" }, it)
                }.isSuccess
            }

            send {
                "关注画师完毕, 关注成功数: ${success?.size ?: 0}, 失败数: ${failure?.size ?: 0}"
            }
        }
        null
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun PixivHelper.getFollowed(uid: Long) = buildSet {
        getUserFollowingPreview(detail = userDetail(uid = uid)).collect { preview ->
            addAll(preview.map { it.user.id })
        }
    }

    @SubCommand
    @Description("为当前助手关注指定用户")
    suspend fun UserCommandSender.user(vararg uid: String) = follow { uid.mapTo(HashSet()) { it.toLong() } }

    @SubCommand
    @Description("关注指定用户的关注")
    suspend fun UserCommandSender.copy(uid: Long) = follow { getFollowed(uid = uid) }
}