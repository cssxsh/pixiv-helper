package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.buildMessageChain
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

    private fun User.getStatistic() = buildMessageChain {
        appendLine("用户: $nameCardOrNick")
        useMappers { mappers ->
            appendLine("使用色图指令次数: ${mappers.statistic.senderEroInfos(id).size}")
            mappers.statistic.senderTagInfos(id).run {
                appendLine("使用标签指令次数: $size")
                groupBy { it.tag }.maxByOrNull { it.value.size }?.let { (tag, list) ->
                    appendLine("检索最多的是 $tag ${list.size} 次")
                }
            }
        }
    }

    private fun Group.getStatistic() = buildMessageChain {
        appendLine("群组: $name")
        useMappers { mappers ->
            mappers.statistic.groupEroInfos(id).run {
                appendLine("使用色图指令次数: $size")
                groupBy { it.sender }.maxByOrNull { it.value.size }?.let { (id, list) ->
                    append("使用最多的是 ")
                    append(At(id))
                    appendLine(" ${list.size} 次")
                }
            }
            mappers.statistic.groupTagInfos(id).run {
                appendLine("使用标签指令次数: $size")
                groupBy { it.sender }.maxByOrNull { it.value.size }?.let { (id, list) ->
                    append("使用最多的是 ")
                    append(At(id))
                    appendLine(" ${list.size} 次")
                }
            }
        }
    }

    @SubCommand
    @Description("获取用户信息")
    suspend fun CommandSenderOnMessage<*>.user(target: User = user as User) = withHelper {
        target.getStatistic()
    }

    @SubCommand
    @Description("获取群组信息")
    suspend fun CommandSenderOnMessage<*>.group(target: Group = subject as Group) = withHelper {
        target.getStatistic()
    }

    @SubCommand
    @Description("获取TAG指令统计信息")
    suspend fun CommandSenderOnMessage<*>.top(limit: Long = TAG_TOP_LIMIT) = withHelper {
        buildMessageChain {
            appendLine("# TAG指令关键词排行")
            appendLine("| index | name | count |")
            appendLine("| --- | --- | --- |")
            useMappers { it.statistic.top(limit = limit) }.forEachIndexed { index, (name, count) ->
                appendLine("| ${index+1} | $name | $count |")
            }
        }
    }

    @SubCommand
    @Description("获取缓存信息")
    suspend fun CommandSenderOnMessage<*>.cache() = withHelper {
        buildMessageChain {
            useMappers {
                appendLine("缓存数: ${it.artwork.count()}")
                appendLine("全年龄色图数: ${it.artwork.eroCount()}")
                appendLine("R18色图数: ${it.artwork.r18Count()}")
            }
        }
    }
}