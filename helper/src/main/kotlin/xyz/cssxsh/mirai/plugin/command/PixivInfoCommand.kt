package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*

object PixivInfoCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "info",
    description = "PIXIV信息指令"
) {

    @SubCommand
    @Description("获取助手信息")
    suspend fun CommandSenderOnMessage<*>.helper() = withHelper {
        val info = info()
        buildMessageChain {
            appendLine("User: ${info.user.uid}")
            appendLine("Name: ${info.user.name}")
            appendLine("Account: ${info.user.account}")
            appendLine("AccessToken: ${info.accessToken}")
            appendLine("ExpiresTime: $expires")
            appendLine("RefreshToken: ${info.refreshToken}")
        }
    }

    @SubCommand
    @Description("获取用户信息")
    suspend fun CommandSenderOnMessage<*>.user(target: User = subject as User) = withHelper {
        buildMessageChain {
            appendLine("用户: ${target.nameCardOrNick}")
            appendLine("使用色图指令次数: ${StatisticEroInfo.user(target.id).size}}")
            StatisticTagInfo.user(target.id).run {
                appendLine("使用标签指令次数: $size")
                groupBy { it.tag }.maxByOrNull { it.value.size }?.let { (tag, list) ->
                    appendLine("检索最多的是 $tag ${list.size} 次")
                }
            }
        }
    }

    @SubCommand
    @Description("获取群组信息")
    suspend fun CommandSenderOnMessage<*>.group(target: Group = subject as Group) = withHelper {
        buildMessageChain {
            appendLine("群组: ${target.name}")
            StatisticEroInfo.group(target.id).run {
                appendLine("使用色图指令次数: $size")
                groupBy { it.sender }.maxByOrNull { it.value.size }?.let { (id, list) ->
                    add("使用最多的是 ")
                    add(At(id))
                    appendLine(" ${list.size} 次")
                }
            }
            StatisticTagInfo.group(target.id).run {
                appendLine("使用标签指令次数: $size")
                groupBy { it.sender }.maxByOrNull { it.value.size }?.let { (id, list) ->
                    add("使用最多的是 ")
                    add(At(id))
                    appendLine(" ${list.size} 次")
                }
                groupBy { it.tag }.maxByOrNull { it.value.size }?.let { (tag, list) ->
                    appendLine("检索最多的是 $tag ${list.size} 次")
                }
            }
        }
    }

    @SubCommand
    @Description("获取TAG指令统计信息")
    suspend fun CommandSenderOnMessage<*>.top(limit: Int = TAG_TOP_LIMIT) = withHelper {
        buildMessageChain {
            appendLine("# TAG指令关键词排行")
            appendLine("| index | name | count |")
            appendLine("| --- | --- | --- |")
            StatisticTagInfo.top(limit = limit).forEachIndexed { index, (name, count) ->
                appendLine("| ${index + 1} | $name | $count |")
            }
        }
    }

    @SubCommand
    @Description("获取缓存信息")
    suspend fun CommandSenderOnMessage<*>.cache() = withHelper {
        buildMessageChain {
            appendLine("缓存数: ${ArtWorkInfo.count()}")
            appendLine("全年龄色图数: ${ArtWorkInfo.eros(false)}")
            appendLine("R18色图数: ${ArtWorkInfo.eros(true)}")
        }
    }
}