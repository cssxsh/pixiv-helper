package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.contact.*
import xyz.cssxsh.mirai.plugin.*

@Suppress("unused")
object PixivInfoCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "info",
    description = "PIXIV信息指令"
) {

    @SubCommand
    @Description("获取助手信息")
    suspend fun CommandSenderOnMessage<*>.helper() = withHelper {
        buildString {
            appendLine("Uid: ${getAuthInfo().user.uid}")
            appendLine("Name: ${getAuthInfo().user.name}")
            appendLine("Account: ${getAuthInfo().user.account}")
            appendLine("Token: ${getAuthInfo().accessToken}")
            appendLine("ExpiresTime: $expiresTime")
        }
    }

    private fun User.getStatistic() = buildString {
        useMappers {
            appendLine("用户: $nameCardOrNick")
            appendLine("使用色图指令次数: ${it.statistic.senderEroInfos(id).size}")
            appendLine("使用标签指令次数: ${it.statistic.senderTagInfos(id).size}")
        }
    }

    private fun Group.getStatistic() = buildString {
        useMappers {
            appendLine("群组: $name")
            appendLine("使用色图指令次数: ${it.statistic.groupEroInfos(id).size}")
            appendLine("使用标签指令次数: ${it.statistic.groupTagInfos(id).size}")
        }
    }

    @SubCommand
    @Description("获取用户信息")
    suspend fun CommandSenderOnMessage<*>.user(target: User) = withHelper {
        target.getStatistic()
    }

    @SubCommand
    @Description("获取群组信息")
    suspend fun CommandSenderOnMessage<*>.group(target: Group) = withHelper {
        target.getStatistic()
    }

    @SubCommand
    @Description("获取当前统计信息")
    suspend fun CommandSenderOnMessage<*>.statistic() = withHelper {
        when(subject) {
            is Group -> (subject as Group).getStatistic()
            is User -> (subject as User).getStatistic()
            else -> "未知联系人: $subject"
        }
    }

    @SubCommand
    @Description("获取缓存信息")
    suspend fun CommandSenderOnMessage<*>.cache() = withHelper {
        buildString {
            useMappers {
                appendLine("缓存数: ${it.artwork.count()}")
                appendLine("全年龄色图数: ${it.artwork.eroCount()}")
                appendLine("R18色图数: ${it.artwork.r18Count()}")
            }
        }
    }
}