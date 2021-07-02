package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.buildMessageChain
import xyz.cssxsh.mirai.plugin.*

object PixivInfoCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "info",
    description = "PIXIV信息指令"
) {

    @SubCommand
    @Description("获取助手信息")
    suspend fun CommandSenderOnMessage<*>.helper() = withHelper {
        buildMessageChain {
            appendLine("User: ${info().user.uid}")
            appendLine("Name: ${info().user.name}")
            appendLine("Account: ${info().user.account}")
            appendLine("Token: ${info().accessToken}")
            appendLine("ExpiresTime: $expires")
        }
    }

    @SubCommand
    @Description("获取用户信息")
    suspend fun CommandSenderOnMessage<*>.user(target: User = subject as User) = withHelper {
        buildMessageChain {
            appendLine("用户: ${target.nameCardOrNick}")
            useMappers { mappers ->
                appendLine("使用色图指令次数: ${mappers.statistic.senderEroInfos(target.id).size}")
                mappers.statistic.senderTagInfos(target.id).run {
                    appendLine("使用标签指令次数: $size")
                    groupBy { it.tag }.maxByOrNull { it.value.size }?.let { (tag, list) ->
                        appendLine("检索最多的是 $tag ${list.size} 次")
                    }
                }
            }
        }
    }

    @SubCommand
    @Description("获取群组信息")
    suspend fun CommandSenderOnMessage<*>.group(target: Group = subject as Group) = withHelper {
        buildMessageChain {
            appendLine("群组: ${target.name}")
            useMappers { mappers ->
                mappers.statistic.groupEroInfos(target.id).run {
                    appendLine("使用色图指令次数: $size")
                    groupBy { it.sender }.maxByOrNull { it.value.size }?.let { (id, list) ->
                        append("使用最多的是 ")
                        append(At(id))
                        append(" ${list.size} 次\n")
                    }
                }
                mappers.statistic.groupTagInfos(target.id).run {
                    appendLine("使用标签指令次数: $size")
                    groupBy { it.sender }.maxByOrNull { it.value.size }?.let { (id, list) ->
                        append("使用最多的是 ")
                        append(At(id))
                        append(" ${list.size} 次\n")
                    }
                    groupBy { it.tag }.maxByOrNull { it.value.size }?.let { (tag, list) ->
                        appendLine("检索最多的是 $tag ${list.size} 次")
                    }
                }
            }
        }
    }

    @SubCommand
    @Description("获取TAG指令统计信息")
    suspend fun CommandSenderOnMessage<*>.top(limit: Long = TAG_TOP_LIMIT) = withHelper {
        buildMessageChain {
            appendLine("# TAG指令关键词排行")
            appendLine("| index | name | count |")
            appendLine("| --- | --- | --- |")
            useMappers { it.statistic.top(limit = limit) }.forEachIndexed { index, (name, count) ->
                appendLine("| ${index + 1} | $name | $count |")
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