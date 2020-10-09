package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.getHelper
import xyz.cssxsh.mirai.plugin.getIllustInfo
import xyz.cssxsh.pixiv.api.app.userFollowAdd

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
                acc
            } else {
                acc + info.user.id
            }
        }.count { uid ->
            runCatching {
                userFollowAdd(uid)
            }.isSuccess
        }
    }.onSuccess {
        quoteReply("关注添加成功, 共${it}个新关注")
    }.onFailure {
        quoteReply("g关注失败， ${it.message}")
    }.isSuccess
}