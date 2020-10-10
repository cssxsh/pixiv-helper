package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.delay
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.utils.secondsToMillis
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.getHelper
import xyz.cssxsh.mirai.plugin.getIllustInfo
import xyz.cssxsh.pixiv.api.app.userDetail
import xyz.cssxsh.pixiv.api.app.userFollowAdd

@Suppress("unused")
object PixivFollowCommand : CompositeCommand(
    PixivHelperPlugin,
    "follow",
    description = "缓存指令",
    prefixOptional = true
), PixivHelperLogger {

    /**
     * 关注色图作者
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.good() = getHelper().runCatching {
        PixivCacheData.values.mapNotNull { pid ->
            getIllustInfo(pid).takeIf { info ->
                info.totalBookmarks ?: 0 >= 10_000 && info.sanityLevel > 4
            }
        }.fold(emptySet<Long>()) { acc, info ->
            if (info.user.isFollowed == true) {
                acc - info.user.id
            } else {
                acc + info.user.id
            }
        }.also {
            logger.verbose("共有${it.size}个用户等待关注")
        }.count { uid ->
            userDetail(uid = uid).let {
                (it.user.isFollowed == false) && runCatching {
                    logger.verbose("添加关注(${it.user.id})[${it.user.name}]")
                    userFollowAdd(it.user.id)
                    delay(30.secondsToMillis)
                }.isSuccess
            }
        }
    }.onSuccess {
        quoteReply("关注添加成功, 共${it}个新关注")
    }.onFailure {
        quoteReply("关注添加失败， ${it.message}")
    }.isSuccess
}