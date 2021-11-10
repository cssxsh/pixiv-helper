package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*

object PixivSettingCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "setting",
    description = "PIXIV设置"
) {

    @SubCommand
    @Description("设置Task连续发送间隔时间, 单位秒")
    suspend fun CommandSenderOnMessage<*>.interval(sec: Int) = withHelper {
        "$TaskSendInterval -> ${sec}s".also { PixivConfigData.interval = sec }
    }

    @SubCommand
    @Description("设置Task通过转发发送")
    suspend fun CommandSenderOnMessage<*>.forward() = withHelper {
        "${!TaskForward}".also { PixivConfigData.forward = !TaskForward }
    }

    @SubCommand
    @Description("设置是否显示Pixiv Cat 原图链接")
    suspend fun CommandSenderOnMessage<*>.link() = withHelper {
        "${!link}".also { link = !link }
    }

    @SubCommand
    @Description("设置是否显示TAG INFO")
    suspend fun CommandSenderOnMessage<*>.tag() = withHelper {
        "${!tag}".also { tag = !tag }
    }

    @SubCommand
    @Description("设置是否显示作品属性")
    suspend fun CommandSenderOnMessage<*>.attr() = withHelper {
        "${!attr}".also { attr = !attr }
    }

    @SubCommand
    @Description("设置cooling置零")
    suspend fun CommandSenderOnMessage<*>.cooling() = withHelper {
        PixivTagCommand.cooling[contact.id] = 0
        "当前用户已置零"
    }

    @SubCommand
    @Description("设置是否显示最大图片数")
    suspend fun CommandSenderOnMessage<*>.max(num: Int) = withHelper {
        "$max -> $num".also { max = num }
    }

    @SubCommand
    @Description("设置发送模式, type: NORMAL, FLASH, RECALL, FORWARD")
    suspend fun CommandSenderOnMessage<*>.model(type: String, ms: Long = 60_000L) = withHelper {
        val new = SendModel(type, ms)
        "$model -> $new".also { model = new }
    }
}