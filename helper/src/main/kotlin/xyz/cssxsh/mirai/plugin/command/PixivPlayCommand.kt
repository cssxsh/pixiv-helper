package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.time.*

object PixivPlayCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "play", "播放",
    description = "PIXIV播放指令"
) {

    override val prefixOptional: Boolean = true

    private var PixivHelper.play by PixivHelperDelegate { CompletedJob }

    private var PixivHelper.duration by PixivHelperDelegate { 10 * 1000L }

    @SubCommand("interval", "间隔")
    @Description("设置间隔")
    suspend fun CommandSenderOnMessage<*>.interval(seconds: Int) = withHelper {
        duration = seconds * 1000L
        "设置间隔 $duration"
    }

    @SubCommand("ranking", "排行榜")
    @Description("根据 排行榜 播放图集")
    suspend fun CommandSenderOnMessage<*>.ranking(mode: RankMode, date: LocalDate? = null) = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val illusts = illustRanking(mode = mode, date = date).illusts
            .apply { replicate() }
            .filter { it.age == AgeLimit.ALL }

        if (duration > 0) {
            play = launch {
                for (illust in illusts) {
                    if (isActive.not()) break
                    delay(duration)

                    runCatching {
                        sendIllust(illust)
                    }.onFailure {
                        logger.warning { "播放错误 $it" }
                    }
                }
            }
            "开始播放[${mode}]排行榜，共${illusts.size}个作品，间隔 ${duration / 1000}s"
        } else {
            if (illusts.isEmpty()) return@withHelper "列表为空"
            buildForwardMessage(contact) {
                for (illust in illusts) {
                    if (isActive.not()) break

                    runCatching {
                        contact.bot says buildMessageByIllust(illust)
                    }.onFailure {
                        logger.warning { "播放错误 $it" }
                    }
                }
            }
        }
    }

    @SubCommand("rank", "排行")
    @Description("根据 words 播放NaviRank")
    suspend fun CommandSenderOnMessage<*>.rank(vararg words: String) = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val rank = NaviRank.getTagRank(words = words)

        if (duration > 0) {
            play = launch {
                for (info in rank.records) {
                    if (isActive.not()) break
                    delay(duration)

                    runCatching {
                        sendIllust(getIllustInfo(pid = info.pid, flush = false))
                    }.onFailure {
                        logger.warning { "播放错误 $it" }
                    }
                }
            }
            "开始播放NaviRank[${rank.title}]，共${rank.records.size}个作品，间隔 $duration"
        } else {
            if (rank.records.isEmpty()) return@withHelper "列表为空"
            buildForwardMessage(contact) {
                for (info in rank.records) {
                    if (isActive.not()) break

                    runCatching {
                        contact.bot says buildMessageByIllust(getIllustInfo(pid = info.pid, flush = false))
                    }.onFailure {
                        logger.warning { "播放错误 $it" }
                    }
                }
            }
        }
    }

    @SubCommand("recommended", "推荐")
    @Description("根据 系统推荐 播放图集")
    suspend fun CommandSenderOnMessage<*>.recommended() = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val user = info().user
        val illusts = illustRecommended().illusts
            .apply { replicate() }
            .filter { it.age == AgeLimit.ALL }

        if (duration > 0) {
            play = launch {
                for (illust in illusts) {
                    if (isActive.not()) break
                    delay(duration)

                    runCatching {
                        sendIllust(illust)
                    }.onFailure {
                        logger.warning { "播放错误 $it" }
                    }
                }
            }
            "开始播放用户[${user.name}]系统推荐，共${illusts.size}个作品，间隔 $duration"
        } else {
            if (illusts.isEmpty()) return@withHelper "列表为空"
            buildForwardMessage(contact) {
                for (illust in illusts) {
                    if (isActive.not()) break

                    runCatching {
                        contact.bot says buildMessageByIllust(illust)
                    }.onFailure {
                        logger.warning { "播放错误 $it" }
                    }
                }
            }
        }
    }

    @SubCommand("mark", "收藏")
    @Description("播放收藏")
    suspend fun CommandSenderOnMessage<*>.mark(tag: String? = null) = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val user = info().user
        val illusts = bookmarksRandom(detail = userDetail(uid = user.uid), tag = tag).illusts

        if (duration > 0) {
            play = launch {
                for (illust in illusts) {
                    if (isActive.not()) break
                    delay(duration)
                    sendIllust(illust)
                }
            }
            "开始播放用户[${user.name}](${tag})收藏，共${illusts.size}个作品，间隔 $duration"
        } else {
            if (illusts.isEmpty()) return@withHelper "列表为空"
            buildForwardMessage(contact) {
                for (illust in illusts) {
                    if (isActive.not()) break

                    runCatching {
                        contact.bot says buildMessageByIllust(illust)
                    }.onFailure {
                        logger.warning { "播放错误 $it" }
                    }
                }
            }
        }
    }

    @SubCommand("article", "特辑")
    @Description("根据 AID 播放特辑")
    suspend fun CommandSenderOnMessage<*>.article(aid: Long) = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val article = Pixivision.getArticle(aid = aid)


        if (duration > 0) {
            play = launch {
                for (info in article.illusts) {
                    if (isActive.not()) break
                    delay(duration)
                    sendIllust(getIllustInfo(pid = info.pid, flush = true))
                }
            }
            "开始播放特辑《${article.title}》，共${article.illusts.size}个作品，间隔 $duration"
        } else {
            if (article.illusts.isEmpty()) return@withHelper "列表为空"
            buildForwardMessage(contact) {
                for (artwork in article.illusts) {
                    if (isActive.not()) break

                    runCatching {
                        contact.bot says buildMessageByIllust(getIllustInfo(pid = artwork.pid, flush = false))
                    }.onFailure {
                        logger.warning { "播放错误 $it" }
                    }
                }
            }
        }
    }

    @SubCommand("walkthrough", "random", "漫游", "随机")
    @Description("根据 AID 播放特辑")
    suspend fun CommandSenderOnMessage<*>.walkthrough() = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val illusts = illustWalkThrough().illusts
            .apply { replicate() }
            .filter { it.age == AgeLimit.ALL && it.isEro() }

        if (duration > 0) {
            play = launch {
                for (illust in illusts) {
                    if (isActive.not()) break
                    delay(duration)
                    sendIllust(illust)
                }
            }
            "开始播放漫游，共${illusts.size}个作品，间隔 $duration"
        } else {
            if (illusts.isEmpty()) return@withHelper "列表为空"
            buildForwardMessage(contact) {
                for (illust in illusts) {
                    if (isActive.not()) break

                    runCatching {
                        contact.bot says buildMessageByIllust(illust)
                    }.onFailure {
                        logger.warning { "播放错误 $it" }
                    }
                }
            }
        }
    }

    @SubCommand("stop", "停止")
    @Description("停止播放当前列表")
    suspend fun CommandSenderOnMessage<*>.stop() = withHelper {
        if (play.isActive) {
            play.cancelAndJoin()
            "当前列表已停止播放"
        } else {
            "当前未播放"
        }
    }
}