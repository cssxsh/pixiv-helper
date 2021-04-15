package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.data.apps.*

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
}