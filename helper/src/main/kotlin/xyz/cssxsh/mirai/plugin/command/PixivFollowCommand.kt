package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.api.apps.*

@Suppress("unused")
object PixivFollowCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "follow",
    description = "PIXIV关注指令"
) {

    @SubCommand
    @Description("为当前助手关注指定用户")
    suspend fun CommandSenderOnMessage<*>.user(uid: Long) = getHelper().runCatching {
        userFollowAdd(uid = uid)
    }.onSuccess {
        logger.info { "添加关注(${uid})成功, $it" }
    }.onFailure {
        quoteReply("关注添加失败， ${it.message}")
    }.isSuccess

    private suspend fun PixivHelper.getFollowed(uid: Long, maxNum: Long = 10_000) = buildSet {
        (0 until maxNum step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
            runCatching {
                userFollowing(uid = uid, offset = offset).userPreviews.map { it.user.id }
            }.onSuccess {
                if (it.isEmpty()) return@buildSet
                addAll(it)
                logger.verbose { "加载(${uid})关注用户预览第${page}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载(${uid})关注用户预览第${page}页失败" }, it)
            }
        }
    }

    private fun PixivHelper.follow(block: suspend PixivHelper.() -> Set<Long>) {
        check(followJob?.isActive != true) { "正在关注中, ${followJob}..." }
        launch(Dispatchers.IO) {
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
        }.also {
            followJob = it
        }
    }

    @SubCommand
    @Description("关注色图缓存中的较好画师")
    suspend fun CommandSenderOnMessage<*>.good() = getHelper().follow {
        val followed = getFollowed(uid = getAuthInfo().user.uid)
        useMappers { it.artwork.userEroCount() }.filter { (_, count) ->
            count > PixivHelperSettings.eroInterval
        }.keys.let {
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
    suspend fun CommandSenderOnMessage<*>.copy(uid: Long) = getHelper().follow {
        getFollowed(uid = uid)
    }
}