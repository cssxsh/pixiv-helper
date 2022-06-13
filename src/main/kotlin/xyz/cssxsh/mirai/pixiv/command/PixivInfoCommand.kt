package xyz.cssxsh.mirai.pixiv.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.util.ContactUtils.render
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.pixiv.*

public object PixivInfoCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "info",
    description = "PIXIV信息指令"
), PixivHelperCommand {

    @SubCommand
    @Description("获取助手信息")
    public suspend fun CommandSender.helper(target: Contact? = subject) {
        val message = if (target == null) {
            "未指定联系人".toPlainText()
        } else {
            buildMessageChain {
                appendLine(target.render())
                val auth = target.helper.client.info()
                appendLine("User: ${auth.user.uid}")
                appendLine("Name: ${auth.user.name}")
                appendLine("Account: ${auth.user.account}")
                appendLine("Premium: ${auth.user.isPremium}")
                appendLine("AccessToken: ${auth.accessToken}")
                appendLine("RefreshToken: ${auth.refreshToken}")
            }
        )
    }

    @SubCommand
    @Description("获取用户信息")
    public suspend fun CommandSender.user(target: User? = user) {
        val message = if (target == null) {
            "未指定用户".toPlainText()
        } else {
            buildMessageChain {
                appendLine("用户: ${target.nameCardOrNick}")
                appendLine("使用色图指令次数: ${StatisticEroInfo.user(target.id).size}")
                with(StatisticTagInfo.user(target.id)) {
                    appendLine("使用标签指令次数: $size")
                    val total = groupBy { it.tag }.entries.sortedByDescending { it.value.size }.take(3)
                    appendLine("检索前三的是")
                    for ((tag, list) in total) {
                        appendLine("$tag ${list.size} 次")
                    }
                }
            }
        }
        sendMessage(message = message)
    }

    @SubCommand
    @Description("获取群组信息")
    public suspend fun CommandSender.group(target: Group? = subject as? Group) {
        val message = if (target == null) {
            "未指定群".toPlainText()
        } else {
            buildMessageChain {
                appendLine("群组: ${target.name}")
                with(StatisticEroInfo.group(target.id)) {
                    appendLine("使用色图指令次数: $size")
                    val senders = groupBy { it.sender }.entries.sortedByDescending { it.value.size }.take(3)
                    appendLine("使用前三的是")
                    for ((id, list) in senders) {
                        add(At(id))
                        appendLine(" ${list.size} 次")
                    }
                }
                with(StatisticTagInfo.group(target.id)) {
                    appendLine("使用标签指令次数: $size")
                    val senders = groupBy { it.sender }.entries.sortedByDescending { it.value.size }.take(3)
                    appendLine("使用前三的用户是")
                    for ((id, list) in senders) {
                        add(At(id))
                        appendLine(" ${list.size} 次")
                    }
                    val tags = groupBy { it.tag }.entries.sortedByDescending { it.value.size }.take(3)
                    appendLine("检索前三的标签是")
                    for ((tag, list) in tags) {
                        appendLine("$tag ${list.size} 次")
                    }
                }
            }
        }
        sendMessage(message = message)
    }

    @SubCommand
    @Description("获取TAG指令统计信息")
    public suspend fun CommandSender.top(limit: Int = TAG_TOP_LIMIT) {
        val message = buildMessageChain {
            appendLine("# TAG指令关键词排行")
            appendLine("| index | name | count |")
            appendLine("| --- | --- | --- |")
            StatisticTagInfo.top(limit).forEachIndexed { index, (name, count) ->
                appendLine("| ${index + 1} | $name | $count |")
            }
        }
        sendMessage(message = message)
    }

    @SubCommand
    @Description("获取缓存信息")
    suspend fun CommandSender.cache() {
        sendMessage(
            message = buildMessageChain {
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
        )
    }
}