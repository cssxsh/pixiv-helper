package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.console.util.ContactUtils.render
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*

object PixivInfoCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "info",
    description = "PIXIV信息指令"
), PixivHelperCommand {

    @SubCommand
    @Description("获取助手信息")
    suspend fun UserCommandSender.helper(target: Contact = subject) = withHelper {
        buildMessageChain {
            @OptIn(ConsoleExperimentalApi::class)
            appendLine(target.render())
            val info = target.helper.info()
            appendLine("User: ${info.user.uid}")
            appendLine("Name: ${info.user.name}")
            appendLine("Account: ${info.user.account}")
            appendLine("Premium: ${info.user.isPremium}")
            appendLine("AccessToken: ${info.accessToken}")
            appendLine("ExpiresTime: ${target.helper.expires}")
            appendLine("RefreshToken: ${info.refreshToken}")
        }
    }

    @SubCommand
    @Description("获取用户信息")
    suspend fun UserCommandSender.user(target: User = subject as User) = withHelper {
        buildMessageChain {
            appendLine("用户: ${target.nameCardOrNick}")
            appendLine("使用色图指令次数: ${StatisticEroInfo.user(target.id).size}")
            with(StatisticTagInfo.user(target.id)) {
                appendLine("使用标签指令次数: $size")
                groupBy { it.tag }.maxByOrNull { it.value.size }?.let { (tag, list) ->
                    appendLine("检索最多的是 $tag ${list.size} 次")
                }
            }
        }
    }

    @SubCommand
    @Description("获取群组信息")
    suspend fun UserCommandSender.group(target: Group = subject as Group) = withHelper {
        buildMessageChain {
            appendLine("群组: ${target.name}")
            with(StatisticEroInfo.group(target.id)) {
                appendLine("使用色图指令次数: $size")
                groupBy { it.sender }.maxByOrNull { it.value.size }?.let { (id, list) ->
                    add("使用最多的是 ")
                    add(At(id))
                    appendLine(" ${list.size} 次")
                }
            }
            with(StatisticTagInfo.group(target.id)) {
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
    suspend fun UserCommandSender.top(limit: Int = TAG_TOP_LIMIT) = withHelper {
        buildMessageChain {
            appendLine("# TAG指令关键词排行")
            appendLine("| index | name | count |")
            appendLine("| --- | --- | --- |")
            StatisticTagInfo.top(limit).forEachIndexed { index, (name, count) ->
                appendLine("| ${index + 1} | $name | $count |")
            }
        }
    }

    @SubCommand
    @Description("获取缓存信息")
    suspend fun UserCommandSender.cache() = withHelper {
        buildMessageChain {
            appendLine("缓存数: ${ArtWorkInfo.count()}")
            appendLine("> ---------")
            appendLine("全年龄色图数: ${ArtWorkInfo.eros(AgeLimit.ALL)}")
            appendLine("R18色图数: ${ArtWorkInfo.eros(AgeLimit.R18)}")
            appendLine("R18G色图数: ${ArtWorkInfo.eros(AgeLimit.R18G)}")
            appendLine("> ---------")
            appendLine("插画色图数: ${ArtWorkInfo.eros(WorkContentType.ILLUST)}")
            appendLine("动画色图数: ${ArtWorkInfo.eros(WorkContentType.UGOIRA)}")
            appendLine("漫画色图数: ${ArtWorkInfo.eros(WorkContentType.MANGA)}")
            appendLine("> ---------")
            appendLine("Sanity(0)色图数: ${ArtWorkInfo.eros(SanityLevel.UNCHECKED)}")
            appendLine("Sanity(1)色图数: ${ArtWorkInfo.eros(SanityLevel.TEMP1)}")
            appendLine("Sanity(2)色图数: ${ArtWorkInfo.eros(SanityLevel.WHITE)}")
            appendLine("Sanity(3)色图数: ${ArtWorkInfo.eros(SanityLevel.TEMP3)}")
            appendLine("Sanity(4)色图数: ${ArtWorkInfo.eros(SanityLevel.SEMI_BLACK)}")
            appendLine("Sanity(5)色图数: ${ArtWorkInfo.eros(SanityLevel.TEMP5)}")
            appendLine("Sanity(6)色图数: ${ArtWorkInfo.eros(SanityLevel.BLACK)}")
            appendLine("Sanity(7)色图数: ${ArtWorkInfo.eros(SanityLevel.NONE)}")
        }
    }
}