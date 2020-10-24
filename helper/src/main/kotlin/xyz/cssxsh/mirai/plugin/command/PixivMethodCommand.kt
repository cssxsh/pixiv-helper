package xyz.cssxsh.mirai.plugin.command

import com.soywiz.klock.PatternDateFormat
import com.soywiz.klock.parseDate
import com.soywiz.klock.wrapped.WDate
import com.soywiz.klock.wrapped.wrapped
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.description.*
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.pixiv.RankMode
import xyz.cssxsh.pixiv.api.app.*
import xyz.cssxsh.pixiv.tool.addIllustFollowListener

@Suppress("unused")
object PixivMethodCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "pixiv",
    description = "pixiv 基本方法",
    prefixOptional = true,
    overrideContext = buildCommandArgumentContext {
        WDate::class with object : CommandArgumentParser<WDate> {
            override fun parse(raw: String, sender: CommandSender): WDate =
                PatternDateFormat("y-M-d").parseDate(raw).wrapped
        }
    }
), PixivHelperLogger {

    /**
     * 登录 通过 用户名，密码
     * @param username 用户名
     * @param password 密码
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.login(
        username: String,
        password: String
    ) = getHelper().runCatching {
        login(username, password).let {
            "${it.user.name} 登陆成功，Token ${it.accessToken}, ExpiresTime: ${getExpiresTimeText()}"
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        logger.warning("登陆失败", it)
        quoteReply("登陆失败， ${it.message}")
    }.isSuccess

    /**
     * 登录 通过 Token
     * @param token refreshToken
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.refresh(
        token: String
    ) = getHelper().runCatching {
        refresh(token).let {
            "${it.user.name} 登陆成功，Token ${it.accessToken}, ExpiresTime: ${getExpiresTimeText()}"
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        logger.warning("登陆失败", it)
        quoteReply("登陆失败， ${it.message}")
    }.isSuccess


    /**
     * 自动登录
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.auto() = getHelper().runCatching {
        autoAuth().let {
            "${it.user.name} 登陆成功，Token ${it.accessToken}, ExpiresTime: ${getExpiresTimeText()}"
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        logger.warning("登陆失败", it)
        quoteReply("登陆失败， ${it.message}")
    }.isSuccess

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
        date: WDate,
        index: Long
    ) = getHelper().runCatching {
        val rankMode: RankMode = enumValueOf(type.also {
            require("18" !in it) { "R18禁止！" }
        })

        buildMessage(illustRanking(date = date, mode = rankMode, offset = index).illusts.first())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("获取排行榜失败， ${it.message}")
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
        buildMessage(illustRanking(mode = rankMode, offset = index).illusts.first())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("获取排行榜失败， ${it.message}")
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
        quoteReply("获取排行榜失败， ${it.message}")
    }.isSuccess

    /**
     * 作品详情
     * @param pid 作品ID
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.detail(
        pid: Long
    ) = getHelper().runCatching {
        buildMessage(illustDetail(pid).illust)
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("获取详情失败， ${it.message}")
    }.isSuccess

    /**
     * 用户作品
     * @param uid 用户ID
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.user(
        uid: Long
    ) = getHelper().runCatching {
        buildMessage(userIllusts(uid).illusts.first())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("获取排行榜失败， ${it.message}")
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
        quoteReply("获取排行榜失败， ${it.message}")
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
        quoteReply("获取排行榜失败， ${it.message}")
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
        quoteReply("监听任务添加失败， ${it.message}")
    }.isSuccess

    /**
     * 书签
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.bookmark() = getHelper().runCatching {
        buildMessage(userBookmarksIllust(uid = getAuthInfo().user.uid).illusts.random())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("读取书签失败， ${it.message}")
    }.isSuccess
}