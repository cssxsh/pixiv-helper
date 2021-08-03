package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.flow.toList
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.message.data.toPlainText
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.apps.PAGE_SIZE

object PixivIllustratorCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "illustrator", "画师",
    description = "PIXIV画师指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    @SubCommand("uid", "id", "user", "用户")
    @Description("根据画师UID随机发送画师作品")
    suspend fun CommandSenderOnMessage<*>.uid(uid: Long) = sendIllust {
        ArtWorkInfo.user(uid).also { list ->
            logger.verbose { "画师(${uid})共找到${list.size}个作品" }
        }.random()
    }

    @SubCommand("name", "名称", "名字")
    @Description("根据画师name或者alias随机发送画师作品")
    suspend fun CommandSenderOnMessage<*>.name(name: String) = sendIllust {
        val uid = requireNotNull(
            (AliasSetting.all().find { it.alias == name }?.uid ?: UserBaseInfo.name(name)?.uid)
        ) { "找不到别名'${name}'" }

        ArtWorkInfo.user(uid).also { list ->
            logger.verbose { "画师(${uid})[${name}]共找到${list.size}个作品" }
        }.random()
    }

    @SubCommand("alias", "别名")
    @Description("设置画师alias")
    suspend fun CommandSenderOnMessage<*>.alias(name: String, uid: Long) = withHelper {
        AliasSetting(alias = name, uid = uid).saveOrUpdate()
        "设置 [$name] -> ($uid)"
    }

    @SubCommand("list", "列表")
    @Description("显示别名列表")
    suspend fun CommandSenderOnMessage<*>.list() = withHelper {
        AliasSetting.all().joinToString("\n") {
            "[${it.alias}] -> (${it.uid})"
        }
    }

    @SubCommand("info", "信息")
    @Description("获取画师信息")
    suspend fun CommandSenderOnMessage<*>.info(uid: Long) = withHelper {
        buildMessageByUser(uid = uid)
    }

    @SubCommand("search", "搜索")
    @Description("搜索画师")
    suspend fun CommandSenderOnMessage<*>.search(name: String, limit: Long = PAGE_SIZE) = withHelper {
        getSearchUser(name = name, limit = limit).toList().flatten().map { preview ->
            "===============>\n".toPlainText() + buildMessageByUser(preview = preview)
        }.toMessageChain()
    }
}