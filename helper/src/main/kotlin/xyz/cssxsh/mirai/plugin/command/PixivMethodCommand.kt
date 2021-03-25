package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.utils.warning
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.api.apps.*
import xyz.cssxsh.pixiv.data.apps.*
import java.time.*

@Suppress("unused")
object PixivMethodCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "pixiv",
    description = "PIXIV基本方法",
    overrideContext = PixivCommandArgumentContext
) {

    private fun IllustData.getRandom() = illusts.writeToCache().random()

    private fun IllustData.getFirst() = illusts.writeToCache().first()

    @SubCommand
    @Description("登录 通过 用户名，密码")
    suspend fun CommandSenderOnMessage<MessageEvent>.login(
        username: String,
        password: String,
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

    @SubCommand
    @Description("登录 通过 Token")
    suspend fun CommandSenderOnMessage<MessageEvent>.refresh(
        token: String,
    ) = getHelper().runCatching {
        refresh(token).let {
            "${it.user.name} 登陆成功，Token ${it.accessToken}, ExpiresTime: $expiresTime"
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        logger.warning({ "[$token]登陆失败" }, it)
        quoteReply("[$token]登陆失败， ${it.message}")
    }.isSuccess

    @SubCommand
    @Description("登录 通过 配置")
    suspend fun CommandSenderOnMessage<MessageEvent>.auto() = getHelper().runCatching {
        autoAuth().let {
            "${it.user.name} 登陆成功，Token ${it.accessToken}, ExpiresTime: $expiresTime"
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
        index: Long,
    ) = getHelper().runCatching {
        buildMessageByIllust(
            illust = illustRanking(date = date, mode = mode, offset = index).getFirst(),
            save = true
        )
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
        index: Long,
    ) = getHelper().runCatching {
        val rankMode: RankMode = enumValueOf(type.also {
            require("18" !in it) { "R18禁止！" }
        })
        buildMessageByIllust(
            illust = illustRanking(mode = rankMode, offset = index).getFirst(),
            save = true
        )
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("获取当今排行榜失败， ${it.message}")
    }.isSuccess

    /**
     * 搜索
     * @param word 关键词
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.search(
        word: String,
    ) = getHelper().runCatching {
        buildMessageByIllust(
            illust = searchIllust(word = word).getRandom(),
            save = true
        )
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
        buildMessageByIllust(
            illust = illustFollow().getRandom(),
            save = true
        )
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("获取排行榜失败， ${it.message}")
    }.isSuccess

    /**
     * 书签
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.bookmark() = getHelper().runCatching {
        buildMessageByIllust(
            illust = userBookmarksIllust(uid = getAuthInfo().user.uid).getRandom(),
            save = true
        )
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("读取书签失败， ${it.message}")
    }.isSuccess
}