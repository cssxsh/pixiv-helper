package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.utils.minutesToMillis
import net.mamoe.mirai.utils.warning
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.RankMode
import xyz.cssxsh.pixiv.api.app.*
import xyz.cssxsh.pixiv.tool.addIllustFollowListener
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Suppress("unused")
object PixivMethodCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "pixiv",
    description = "PIXIV基本方法",
    overrideContext = buildCommandArgumentContext {
        RankMode::class with object : CommandValueArgumentParser<RankMode> {
            override fun parse(raw: String, sender: CommandSender): RankMode =
                enumValueOf(raw.toUpperCase())
        }
        LocalDate::class with object : CommandValueArgumentParser<LocalDate> {
            override fun parse(raw: String, sender: CommandSender): LocalDate =
                LocalDate.parse(raw, DateTimeFormatter.ISO_DATE)
        }
    }
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

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
            "${it.user.name} 登陆成功，Token ${it.accessToken}, ExpiresTime: $expiresTime"
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        logger.warning({ "[$username]登陆失败" }, it)
        quoteReply("[$username]登陆失败， ${it.message}")
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
            "${it.user.name} 登陆成功，Token ${it.accessToken}, ExpiresTime: ${expiresTime}"
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        logger.warning({ "[$token]登陆失败" }, it)
        quoteReply("[$token]登陆失败， ${it.message}")
    }.isSuccess


    /**
     * 自动登录
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.auto() = getHelper().runCatching {
        autoAuth().let {
            "${it.user.name} 登陆成功，Token ${it.accessToken}, ExpiresTime: ${expiresTime}"
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        logger.warning({ "自动登陆失败" }, it)
        quoteReply("自动登陆失败， ${it.message}")
    }.isSuccess

    /**
     * 排行榜
     * @param mode 模式名 [RankMode]
     * @param date 日期 yyyy-MM-dd
     * @param index 排名
     */
    @SubCommand
    @Description("type by in DAY, DAY_MALE, DAY_FEMALE, WEEK_ORIGINAL, WEEK_ROOKIE, WEEK, MONTH, DAY_MANGA")
    suspend fun CommandSenderOnMessage<MessageEvent>.rank(
        mode: RankMode,
        date: LocalDate,
        index: Long
    ) = getHelper().runCatching {
        buildMessage(illustRanking(date = date, mode = mode, offset = index, ignore = apiIgnore).illusts.apply { writeToCache() }.first())
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
        buildMessage(illustRanking(mode = rankMode, offset = index, ignore = apiIgnore).illusts.apply { writeToCache() }.first())
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
        buildMessage(illustRanking(mode = RankMode.values().random(), ignore = apiIgnore).illusts.apply { writeToCache() }.random())
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
        buildMessage(illustDetail(pid = pid, ignore = apiIgnore).illust.apply { writeToCache() })
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
        buildMessage(userIllusts(uid = uid, ignore = apiIgnore).illusts.apply { writeToCache() }.first())
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
        require(index in 1..30) { "index 的范围在1~30" }
        buildMessage(searchIllust(word = word, ignore = apiIgnore).illusts.apply { writeToCache() }[index - 1])
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
        buildMessage(illustFollow(ignore = apiIgnore).illusts.apply { writeToCache() }.random())
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
        addIllustFollowListener(delay = (10).minutesToMillis) {
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
        buildMessage(userBookmarksIllust(uid = getAuthInfo().user.uid, ignore = apiIgnore).illusts.apply { writeToCache() }.random())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("读取书签失败， ${it.message}")
    }.isSuccess
}