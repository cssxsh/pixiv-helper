package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.flow.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.apps.*

object PixivIllustratorCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "illustrator", "画师",
    description = "PIXIV画师指令"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    @SubCommand("uid", "id", "user", "用户")
    @Description("根据画师UID随机发送画师作品")
    suspend fun UserCommandSender.uid(uid: Long) = withHelper {
        ArtWorkInfo.user(uid).randomOrNull() ?: "画师 $uid 没有找到缓存"
    }

    @SubCommand("name", "名称", "名字", "推特")
    @Description("根据画师name或者alias或twitter随机发送画师作品")
    suspend fun UserCommandSender.name(name: String) = withHelper {
        val author = requireNotNull(AliasSetting[name] ?: UserBaseInfo.like(name) ?: Twitter[name]) { "找不到名称'${name}'" }

        ArtWorkInfo.user(author.uid).randomOrNull() ?: "画师 $name 没有找到缓存"
    }

    @SubCommand("alias", "别名")
    @Description("设置画师alias")
    suspend fun UserCommandSender.alias(name: String, uid: Long) = withHelper {
        if (uid > 0) {
            AliasSetting(alias = name, uid = uid).replicate()
            "设置 [$name] -> ($uid)"
        } else {
            AliasSetting.delete(alias = name)
            "删除"
        }
    }

    @SubCommand("list", "列表")
    @Description("显示别名列表")
    suspend fun UserCommandSender.list() = withHelper {
        AliasSetting.all().joinToString("\n") {
            "[${it.alias}] -> (${it.uid})"
        }
    }

    @SubCommand("info", "信息")
    @Description("获取画师信息")
    suspend fun UserCommandSender.info(uid: Long) = withHelper {
        buildMessageByUser(uid = uid)
    }

    @SubCommand("search", "搜索")
    @Description("搜索画师")
    suspend fun UserCommandSender.search(name: String, limit: Long = PAGE_SIZE) = withHelper {
        getSearchUser(name = name, limit = limit).toList().flatten().map { preview ->
            "===============>\n".toPlainText() + buildMessageByUser(preview = preview)
        }.toMessageChain()
    }
}