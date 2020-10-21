package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.delayTime
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

        check(followJob?.isActive != true) { "正在关注中, ${cacheJob}..." }
        PixivCacheData.caches().values.filter { info ->
            info.isEro()
        }.map { info ->
            info.uid
        }.sorted().toSet().let {
            it - followed
        }.also {
            logger.verbose("已关注${followed.size}, 共有${it.size}个用户等待关注")
        }.run {
            size to launch {
                runCatching {
                    size to count { uid ->
                        isActive && runCatching {
                            delay(delayTime)
                            userFollowAdd(uid)
                        }.onSuccess {
                            logger.info("用户(${getAuthInfo().user.name})[${getAuthInfo().user.uid}]添加关注(${uid})成功, $it")
                        }.onFailure {
                            logger.warning("用户(${getAuthInfo().user.name})[${getAuthInfo().user.uid}]添加关注(${uid})失败", it)
                        }.isSuccess
                    }
                }.onSuccess { (total, success) ->
                    quoteReply("关注完毕共${total}个画师, 关注成功${success}个")
                }.onFailure {
                    quoteReply("关注失败, ${it.message}")
                }
            }.also {
                followJob = it
            }
        }
    }.onSuccess { (total, job) ->
        quoteReply("共${total}个画师等待关注, 添加任务完成${job}")
    }.onFailure {
        quoteReply("关注添加失败， ${it.message}")
    }.isSuccess
}