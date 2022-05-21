package xyz.cssxsh.mirai.pixiv.command

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.mirai.pixiv.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.time.*

object PixivPlayCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "play", "播放",
    description = "PIXIV播放指令"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    private var PixivHelper.play by PixivHelperDelegate { CompletedJob }

    private var PixivHelper.duration by PixivHelperDelegate { 10 * 1000L }

    @SubCommand("interval", "间隔")
    @Description("设置间隔")
    suspend fun UserCommandSender.interval(seconds: Int) = withHelper {
        duration = seconds * 1000L
        "设置间隔 $duration"
    }

    private fun UserCommandSender.play(illusts: List<IllustInfo>) = launch {
        for (illust in illusts) {
            if (isActive.not()) break
            delay(helper.duration)

            try {
                sendIllust(illust)
            } catch (e: Throwable) {
                logger.warning({ "播放错误" }, e)
            }
        }
    }

    private suspend fun PixivHelper.forward(illusts: List<IllustInfo>, title: String): Message {
        if (illusts.isEmpty()) return "列表为空".toPlainText()

        contact.sendMessage("开始将${illusts.size}个作品合成转发消息，请稍后...")

        val list = illusts.map { illust ->
            val sender = (contact as? User) ?: (contact as Group).members.random()
            async {
                try {
                    ForwardMessage.Node(
                        senderId = sender.id,
                        senderName = sender.nameCardOrNick,
                        time = illust.createAt.toEpochSecond().toInt(),
                        message = buildMessageByIllust(illust = illust)
                    )
                } catch (e: Throwable) {
                    logger.warning({ "播放错误" }, e)
                    ForwardMessage.Node(
                        senderId = sender.id,
                        senderName = sender.nameCardOrNick,
                        time = illust.createAt.toEpochSecond().toInt(),
                        message = "[${illust.pid}]构建失败 ${e.message.orEmpty()}".toPlainText()
                    )
                }
            }
        }.awaitAll()

        logger.info { "play list 合成完毕" }
        return RawForwardMessage(list).render {
            this.title = title
        }
    }

    @SubCommand("ranking", "排行榜")
    @Description("根据 排行榜 播放图集")
    suspend fun UserCommandSender.ranking(mode: RankMode, date: LocalDate? = null) = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val illusts = illustRanking(mode = mode, date = date).illusts
            .apply { replicate() }
            .filter { it.age == AgeLimit.ALL }

        if (model != SendModel.Forward && duration > 0) {
            play = play(illusts = illusts)
            "开始播放[${mode}]排行榜，共${illusts.size}个作品，间隔 ${duration / 1000}s"
        } else {
            forward(illusts = illusts, title = "[${mode}]排行榜")
        }
    }

    @SubCommand("rank", "排行")
    @Description("根据 words 播放NaviRank")
    suspend fun UserCommandSender.rank(vararg words: String) = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val rank = NaviRank.getTagRank(words = words)

        if (model != SendModel.Forward && duration > 0) {
            play = launch {
                for (info in rank.records) {
                    if (isActive.not()) break
                    delay(duration)

                    try {
                        sendIllust(getIllustInfo(pid = info.pid, flush = false))
                    } catch (e: Throwable) {
                        logger.warning({ "播放错误" }, e)
                    }
                }
            }
            "开始播放NaviRank[${rank.title}]，共${rank.records.size}个作品，间隔 $duration"
        } else {
            if (rank.records.isEmpty()) return@withHelper "列表为空"
            val list = rank.records.map { info ->
                val sender = (subject as? User) ?: (subject as Group).members.random()
                async {
                    try {
                        val illust = getIllustInfo(pid = info.pid, flush = false)
                        ForwardMessage.Node(
                            senderId = sender.id,
                            senderName = sender.nameCardOrNick,
                            time = illust.createAt.toEpochSecond().toInt(),
                            message = buildMessageByIllust(illust = illust)
                        )
                    } catch (e: Throwable) {
                        logger.warning({ "播放错误" }, e)
                        ForwardMessage.Node(
                            senderId = sender.id,
                            senderName = sender.nameCardOrNick,
                            time = info.date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond().toInt(),
                            message = "[${info.pid}]构建失败 ${e.message.orEmpty()}".toPlainText()
                        )
                    }
                }
            }.awaitAll()

            RawForwardMessage(list).render {
                title = "NaviRank[${rank.title}]"
            }
        }
    }

    @SubCommand("recommended", "推荐")
    @Description("根据 系统推荐 播放图集")
    suspend fun UserCommandSender.recommended() = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val user = info().user
        val illusts = illustRecommended().illusts
            .apply { replicate() }
            .filter { it.age == AgeLimit.ALL }

        if (model != SendModel.Forward && duration > 0) {
            play = play(illusts)
            "开始播放用户[${user.name}]系统推荐，共${illusts.size}个作品，间隔 $duration"
        } else {
            forward(illusts = illusts, title = "recommended")
        }
    }

    @SubCommand("mark", "收藏")
    @Description("播放收藏")
    suspend fun UserCommandSender.mark(tag: String? = null) = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val user = info().user
        val illusts = getBookmarksRandom(detail = userDetail(uid = user.uid), tag = tag).illusts
            .apply { replicate() }

        if (model != SendModel.Forward && duration > 0) {
            play = play(illusts)
            "开始播放用户[${user.name}](${tag})收藏，共${illusts.size}个作品，间隔 $duration"
        } else {
            forward(illusts = illusts, title = "mark")
        }
    }

    @SubCommand("article", "特辑")
    @Description("根据 AID 播放特辑")
    suspend fun UserCommandSender.article(aid: Long) = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val article = Pixivision.getArticle(aid = aid)

        if (model != SendModel.Forward && duration > 0) {
            play = launch {
                for (info in article.illusts) {
                    if (isActive.not()) break
                    delay(duration)
                    sendIllust(getIllustInfo(pid = info.pid, flush = true))
                }
            }
            "开始播放特辑《${article.title}》，共${article.illusts.size}个作品，间隔 $duration"
        } else {
            val illusts = getListIllusts(info = article.illusts).toList().flatten()
            forward(illusts = illusts, title = "特辑《${article.title}》")
        }
    }

    @SubCommand("walkthrough", "random", "漫游", "随机")
    @Description("漫游")
    suspend fun UserCommandSender.walkthrough() = withHelper {
        check(!play.isActive) { "其他列表播放中" }
        val illusts = illustWalkThrough().illusts
            .apply { replicate() }
            .filter { it.age == AgeLimit.ALL && it.isEro() }

        if (model != SendModel.Forward && duration > 0) {
            play = play(illusts = illusts)
            "开始播放漫游，共${illusts.size}个作品，间隔 $duration"
        } else {
            forward(illusts = illusts, title = "walkthrough")
        }
    }

    @SubCommand("stop", "停止")
    @Description("停止播放当前列表")
    suspend fun UserCommandSender.stop() = withHelper {
        if (play.isActive) {
            play.cancelAndJoin()
            "当前列表已停止播放"
        } else {
            "当前未播放"
        }
    }
}