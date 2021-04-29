package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.Contact
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.AgeLimit
import xyz.cssxsh.pixiv.apps.*
import kotlin.time.seconds

object PixivPlayCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "play", "播放",
    description = "PIXIV播放指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private val jobs = mutableMapOf<Contact, Job>()

    @SubCommand("rank", "排行")
    @Description("根据 Tag 播放特辑")
    suspend fun CommandSenderOnMessage<*>.rank(vararg words: String, seconds: Int = 10) = withHelper {
        check(jobs[contact]?.isActive != true) { "其他列表播放着中" }
        val duration = maxOf(seconds.seconds, interval)
        val rank = NaviRank.getTagRank(words = words)
        jobs[contact] = launch {
            rank.records.cached().forEach {
                delay(duration)
                if (isActive) sendIllust { it }
            }
        }
        "开始播放NaviRank[${rank.title}]，共${rank.records.size}个作品，间隔 $duration"
    }

    @SubCommand("recommended", "推荐")
    @Description("根据 系统推荐 播放图集")
    suspend fun CommandSenderOnMessage<*>.recommended(seconds: Int = 10) = withHelper {
        check(jobs[contact]?.isActive != true) { "其他列表播放着中" }
        val duration = maxOf(seconds.seconds, interval)
        val user = getAuthInfo().user
        val illusts = illustRecommended().illusts.filter { it.age == AgeLimit.ALL }
        jobs[contact] = launch {
            illusts.forEach {
                delay(duration)
                if (isActive) sendIllust(flush = true) { it }
            }
        }
        "开始播放用户[${user.name}]系统推荐，共${illusts.size}个作品，间隔 $duration"
    }

    @SubCommand("mark", "收藏")
    @Description("根据 tag 播放收藏")
    suspend fun CommandSenderOnMessage<*>.mark(tag: String? = null, seconds: Int = 10) = withHelper {
        check(jobs[contact]?.isActive != true) { "其他列表播放着中" }
        val duration = maxOf(seconds.seconds, interval)
        val user = getAuthInfo().user
        val illusts = bookmarksRandom(uid = user.uid, tag = tag).illusts
        jobs[contact] = launch {
            illusts.forEach {
                delay(duration)
                if (isActive) sendIllust(flush = true) { it }
            }
        }
        "开始播放用户[${user.name}](${tag})收藏，共${illusts.size}个作品，间隔 $duration"
    }

    @SubCommand("article", "特辑")
    @Description("根据 AID 播放特辑")
    suspend fun CommandSenderOnMessage<*>.article(aid: Long, seconds: Int = 10) = withHelper {
        check(jobs[contact]?.isActive != true) { "其他列表播放着中" }
        val duration = maxOf(seconds.seconds, interval)
        val article = Pixivision.getArticle(aid = aid)
        jobs[contact] = launch {
            article.illusts.forEach {
                delay(duration)
                if (isActive) sendIllust(flush = false) {
                    getIllustInfo(pid = it.pid, flush = false)
                }
            }
        }
        "开始播放特辑《${article.title}》，共${article.illusts.size}个作品，间隔 $duration"
    }

    @SubCommand("stop", "停止")
    @Description("停止播放当前列表")
    suspend fun CommandSenderOnMessage<*>.stop() = withHelper {
        jobs.remove(contact)?.let { it.cancelAndJoin(); "当前列表已停止播放" } ?: "当前未播放"
    }
}