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

    /**
     * 关注色图作者
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.good() = getHelper().runCatching {
         val authInfo = getAuthInfo()
         val followed = buildList {
            (0L until 10_000 step AppApi.PAGE_SIZE).forEach { offset ->
                runCatching {
                    userFollowing(uid = authInfo.user.uid, offset = offset).userPreviews.map { it.user.id }
                }.onSuccess {
                    if (it.isEmpty()) return@buildList
                    add(it)
                    logger.verbose("加载关注用户作品预览第${offset / 30}页{${it.size}}成功")
                }.onFailure {
                    logger.verbose("加载关注用户作品预览第${offset / 30}页失败", it)
                }
            }
        }.flatten().toSet()

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
                    userFollowAdd(uid)
                }.onSuccess {
                    logger.info("用户(${authInfo.user.name})添加关注(${uid})成功, $it")
                    delay(3.secondsToMillis)
                }.onFailure {
                    logger.warning("用户(${authInfo.user.name})添加关注(${uid})失败", it)
                }.isSuccess
            }
        }
    }.onSuccess { (size, count) ->
        quoteReply("关注添加成功, 共${size}个新关注, ${count}个关注成功。")
    }.onFailure {
        quoteReply("关注添加失败， ${it.message}")
    }.isSuccess
}