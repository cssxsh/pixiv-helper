package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*

object PixivIllustratorCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "illustrator", "画师",
    description = "PIXIV画师指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    @SubCommand("uid", "id", "user")
    @Description("根据画师UID随机发送画师作品")
    suspend fun CommandSenderOnMessage<*>.uid(uid: Long) = sendIllust {
        useMappers { it.artwork.userArtWork(uid) }.also { list ->
            logger.verbose { "画师(${uid})共找到${list.size}个作品" }
        }.random()
    }

    @SubCommand("name", "名称")
    @Description("根据画师name或者alias随机发送画师作品")
    suspend fun CommandSenderOnMessage<*>.name(name: String) = sendIllust {
        useMappers { mappers ->
            mappers.statistic.alias().find { it.alias == name }?.uid ?: mappers.user.findByName(name).randomOrNull()?.uid
        }.let { requireNotNull(it) { "找不到别名'${name}'" } }.let { uid ->
            useMappers { it.artwork.userArtWork(uid) }.also { list ->
                logger.verbose { "画师(${uid})[${name}]共找到${list.size}个作品" }
            }.random()
        }
    }

    @SubCommand("alias", "别名")
    @Description("设置画师或alias")
    suspend fun CommandSenderOnMessage<*>.alias(name: String, uid: Long) = withHelper {
        useMappers { it.statistic.replaceAliasSetting(AliasSetting(alias = name, uid = uid)) }
        "设置 [$name] -> ($uid)"
    }

    @SubCommand("list", "列表")
    @Description("显示别名列表")
    suspend fun CommandSenderOnMessage<*>.list() = withHelper {
        useMappers { it.statistic.alias() }.joinToString("\n") { "[${it.alias}] -> (${it.uid})" }
    }

    @SubCommand("info", "信息")
    @Description("获取画师信息")
    suspend fun CommandSenderOnMessage<*>.info(uid: Long) = withHelper {
        buildMessageByUser(uid = uid, save = true)
    }
}