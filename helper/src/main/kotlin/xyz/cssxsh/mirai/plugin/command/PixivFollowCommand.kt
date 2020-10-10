package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.MessageEvent
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
    description = "关注指令",
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
            if (info.user.isFollowed == false) {
                acc + info.user.id
            } else {
                acc - info.user.id
            }
        }.also {
            logger.verbose("共有${it.size}个用户等待关注")
        }.mapNotNull { uid ->
            runCatching {
                userDetail(uid = uid).user
            }.onSuccess { user ->
                logger.verbose("用户(${user.id})[${user.name}]状态加载完毕.")
            }.onFailure {
                logger.verbose("用户(${uid})状态加载失败", it)
            }.getOrNull()
        }.count { user ->
            (user.isFollowed == false) && runCatching {
                logger.info("添加关注(${user.id})[${user.name}]")
                userFollowAdd(user.id)
            }.isSuccess
        }
    }.onSuccess {
        quoteReply("关注添加成功, 共${it}个新关注")
    }.onFailure {
        quoteReply("关注添加失败， ${it.message}")
    }.isSuccess
}