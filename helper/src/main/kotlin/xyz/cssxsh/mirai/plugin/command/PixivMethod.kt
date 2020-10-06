package xyz.cssxsh.mirai.plugin.command

import com.soywiz.klock.PatternDateFormat
import com.soywiz.klock.parseDate
import com.soywiz.klock.wrapped.WDate
import com.soywiz.klock.wrapped.wrapped
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.pixiv.RankMode
import xyz.cssxsh.pixiv.api.app.*
import xyz.cssxsh.pixiv.client.exception.NotLoginException
import xyz.cssxsh.pixiv.tool.addIllustFollowListener

@Suppress("unused")
object PixivMethod : CompositeCommand(
    PixivHelperPlugin,
    "pixiv",
    description = "pixiv 基本方法",
    prefixOptional = true
), PixivHelperLogger {

    /**
     * 排行榜
     * @param type 模式名 [RankMode]
     * @param date 日期 yyyy-MM-dd
     * @param index 排名
     */
    @SubCommand
    @Description("type by in DAY, DAY_MALE, DAY_FEMALE, WEEK_ORIGINAL, WEEK_ROOKIE, WEEK, MONTH, DAY_MANGA")
    suspend fun CommandSenderOnMessage<MessageEvent>.rank(
        type: String,
        date: String,
        index: Long
    ) = getHelper().runCatching {
        val rankMode: RankMode = enumValueOf(type.also {
            require("18" !in it) { "R18禁止！" }
        })
        val wDate: WDate = PatternDateFormat("y-M-d").parseDate(date).wrapped

        buildMessage(illustRanking(date = wDate, mode = rankMode, offset = index.positiveLongCheck()).illusts.first())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 当前排行榜
     * @param type 模式名 [RankMode]
     * @param index 排名
     */
    @SubCommand
    @Description("type by in DAY, DAY_MALE, DAY_FEMALE, WEEK_ORIGINAL, WEEK_ROOKIE, WEEK, MONTH, DAY_MANGA")
    suspend fun CommandSenderOnMessage<MessageEvent>.now(
        type: String,
        index: Long
    ) = getHelper().runCatching {
        val rankMode: RankMode = enumValueOf(type.also {
            require("18" !in it) { "R18禁止！" }
        })
        buildMessage(illustRanking(mode = rankMode, offset = index.positiveLongCheck()).illusts.first())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 随机排行榜
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.random() = getHelper().runCatching {
        val rankMode: RankMode = RankMode.values().random()
        buildMessage(illustRanking(mode = rankMode).illusts.random())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 作品详情
     * @param pid 作品ID
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.detail(
        pid: Long
    ) = getHelper().runCatching {
        buildMessage(illustDetail(pid.positiveLongCheck()).illust)
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 用户作品
     * @param uid 用户ID
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.user(
        uid: Long
    ) = getHelper().runCatching {
        buildMessage(userIllusts(uid.positiveLongCheck()).illusts.first())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 搜索
     * @param word 关键词
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.search(
        word: String,
        index: Int
    ) = getHelper().runCatching {
        require(index in 1..30) {  "index 的范围在1~30" }
        buildMessage(searchIllust(word).illusts[index - 1])
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 关注
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.follow() = getHelper().runCatching {
        buildMessage(illustFollow().illusts.random())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 监听
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.listen() = getHelper().runCatching {
        addIllustFollowListener {
            buildMessage(it).forEach { message -> reply(message) }
        }
    }.onSuccess {
        quoteReply("监听任务添加成功")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 书签
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.bookmark() = getHelper().runCatching {
        buildMessage(userBookmarksIllust(uid = (authInfo ?: throw NotLoginException()).user.uid).illusts.random())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}