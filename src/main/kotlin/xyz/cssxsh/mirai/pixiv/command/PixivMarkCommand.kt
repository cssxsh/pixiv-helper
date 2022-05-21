package xyz.cssxsh.mirai.pixiv.command

import kotlinx.coroutines.flow.*
import net.mamoe.mirai.console.command.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.pixiv.apps.*

object PixivMarkCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "mark", "bookmark",
    description = "PIXIV收藏指令"
), PixivHelperCommand {

    @SubCommand
    @Description("添加指定作品收藏，并设定TAG")
    suspend fun UserCommandSender.add(pid: Long, vararg words: String) = withHelper {
        illustBookmarkAdd(pid = pid, tags = words.toSet())
        "收藏${pid} -> [${words.joinToString()}] 成功"
    }

    @SubCommand
    @Description("删除指定作品收藏")
    suspend fun UserCommandSender.delete(pid: Long) = withHelper {
        illustBookmarkDelete(pid = pid)
        "取消收藏${pid}成功"
    }

    @SubCommand
    @Description("随机发送一个收藏的作品")
    suspend fun UserCommandSender.random(tag: String? = null) = withHelper {
        getBookmarks(uid = info().user.uid, tag = tag).also { flow ->
            addCacheJob(name = "MARK_RANDOM", reply = false) { flow }
        }.toList().flatten().random()
    }

    @SubCommand
    @Description("显示收藏列表")
    suspend fun UserCommandSender.list() = withHelper {
        getBookmarkTagInfos().toList().flatten().joinToString("\n") { (name, count) ->
            "[${name}] -> $count"
        }
    }
}