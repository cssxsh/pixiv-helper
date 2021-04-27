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
import xyz.cssxsh.pixiv.apps.illustRecommended
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
        check(play?.isActive != true) { "其他列表播放着中" }
        val duration = maxOf(seconds.seconds, interval)
        NaviRank.getTagRank(words = words).run {
            play = launch {
                records.cached().forEach {
                    delay(duration)
                    if (isActive) sendIllust { it }
                }
            }
            "开始播放NaviRank[${title}]，共${records.size}个作品，间隔 $duration"
        }
    }

    @SubCommand("recommended", "推荐")
    @Description("根据 系统推荐 播放图集")
    suspend fun CommandSenderOnMessage<*>.recommended(seconds: Int = 10) = withHelper {
        check(play?.isActive != true) { "其他列表播放着中" }
        val duration = maxOf(seconds.seconds, interval)
        val user = getAuthInfo().user
        illustRecommended().run {
            play = launch {
                illusts.filter { it.age == AgeLimit.ALL }.forEach {
                    delay(duration)
                    if (isActive) sendIllust(flush = true) { it }
                }
            }
            "开始播放用户[${user.name}]系统推荐，共${illusts.size}个作品，间隔 $duration"
        }
    }

    @SubCommand("mark", "收藏")
    @Description("根据 tag 播放收藏")
    suspend fun CommandSenderOnMessage<*>.mark(tag: String? = null, seconds: Int = 10) = withHelper {
        check(play?.isActive != true) { "其他列表播放着中" }
        val duration = maxOf(seconds.seconds, interval)
        val user = getAuthInfo().user
        bookmarksRandom(uid = user.uid, tag = tag).run {
            play = launch {
                illusts.forEach {
                    delay(duration)
                    if (isActive) sendIllust(flush = true) { it }
                }
            }
            "开始播放用户[${user.name}](${tag})收藏，共${illusts.size}个作品，间隔 $duration"
        }
    }

    @SubCommand("article", "特辑")
    @Description("根据 AID 播放特辑")
    suspend fun CommandSenderOnMessage<*>.article(aid: Long, seconds: Int = 10) = withHelper {
        check(play?.isActive != true) { "其他列表播放着中" }
        val duration = maxOf(seconds.seconds, interval)
        Pixivision.getArticle(aid = aid).run {
            play = launch {
                illusts.forEach {
                    delay(duration)
                    if (isActive) sendIllust(flush = false) {
                        getIllustInfo(pid = it.pid, flush = false)
                    }
                }
            }
            "开始播放特辑《${title}》，共${illusts.size}个作品，间隔 $duration"
        }
    }

    @SubCommand("stop", "停止")
    @Description("停止播放特辑")
    suspend fun CommandSenderOnMessage<*>.stop() = withHelper {
        jobs.remove(contact)?.let { it.cancelAndJoin(); "当前特辑已停止播放" } ?: "当前未播放特辑"
    }
}