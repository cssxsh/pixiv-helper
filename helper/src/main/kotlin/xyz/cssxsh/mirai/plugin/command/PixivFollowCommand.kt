package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.pixiv.api.app.userFollowAdd
import xyz.cssxsh.pixiv.api.app.userFollowing

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
        val authInfo = getAuthInfoOrThrow()
         val followed = buildList {
            (0 until 100).forEach { index ->
                runCatching {
                    userFollowing(uid = authInfo.user.uid, offset = index * 30L).userPreviews.map { it.user.id }
                }.onSuccess {
                    if (it.isEmpty()) return@buildList
                    add(it)
                    logger.verbose("加载关注用户作品预览第${index}页{${it.size}}成功")
                }.onFailure {
                    logger.verbose("加载关注用户作品预览第${index}页失败", it)
                }
            }
        }.flatten().toSet()

        PixivCacheData.values().mapNotNull { info ->
            getIllustInfo(info.pid).takeIf {
                it.isEro()
            }
        }.map {
            it.user.id
        }.toSet().let {
            it - followed
        }.also {
            logger.verbose("共有${it.size}个用户等待关注")
        }.count { uid ->
            runCatching {
                userFollowAdd(uid).let {
                    logger.info("用户(${authInfo.user.name})添加关注(${uid}), $it")
                }
            }.isSuccess
        }
    }.onSuccess {
        quoteReply("关注添加成功, 共${it}个新关注")
    }.onFailure {
        quoteReply("关注添加失败， ${it.message}")
    }.isSuccess
}