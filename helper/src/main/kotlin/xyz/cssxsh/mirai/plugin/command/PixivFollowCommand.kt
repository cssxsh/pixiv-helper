package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.delay
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.utils.secondsToMillis
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.pixiv.api.app.*

@Suppress("unused")
object PixivFollowCommand : CompositeCommand(
    PixivHelperPlugin,
    "follow",
    description = "关注指令",
    prefixOptional = true
), PixivHelperLogger {

    private suspend fun PixivHelper.getFollowed(uid: Long, maxNum: Long = 10_000): Set<Long> = buildList {
        (0L until maxNum step AppApi.PAGE_SIZE).forEach { offset ->
            runCatching {
                userFollowing(uid = uid, offset = offset).userPreviews.map { it.user.id }
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                add(it)
                logger.verbose("加载关注用户作品预览第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.warning("加载关注用户作品预览第${offset / 30}页失败", it)
            }
        }
    }.flatten().toSet()

    /**
     * 关注色图作者
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.ero() = getHelper().runCatching {
         val followed = getFollowed(uid = getAuthInfo().user.uid)

        PixivCacheData.caches().values.filter { info ->
            info.isEro()
        }.map { info ->
            info.uid
        }.toSet().let {
            it - followed
        }.also {
            logger.verbose("已关注${followed.size}, 共有${it.size}个用户等待关注")
        }.run {
            size to count { uid ->
                runCatching {
                    delay(30.secondsToMillis)
                    userFollowAdd(uid)
                }.onSuccess {
                    logger.info("用户(${getAuthInfo().user.name})[${getAuthInfo().user.uid}]添加关注(${uid})成功, $it")
                }.onFailure {
                    logger.warning("用户(${getAuthInfo().user.name})[${getAuthInfo().user.uid}]添加关注(${uid})失败", it)
                }.isSuccess
            }
        }
    }.onSuccess { (size, count) ->
        quoteReply("关注添加成功, 共${size}个新关注, ${count}个关注成功。")
    }.onFailure {
        quoteReply("关注添加失败， ${it.message}")
    }.isSuccess
}