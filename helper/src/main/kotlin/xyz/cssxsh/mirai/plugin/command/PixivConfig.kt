package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.data.PixivHelperData
import xyz.cssxsh.mirai.plugin.getHelper
import xyz.cssxsh.pixiv.client.exception.NotLoginException

@Suppress("unused")
object PixivConfig: CompositeCommand(
    PixivHelperPlugin,
    "config",
    description = "pixiv 设置",
    prefixOptional = true
), PixivHelperLogger {
    /**
     * 设置代理 pixiv proxy http://10.21.159.95:7890
     * @param proxy 代理URL
     */
    @SubCommand
    fun ConsoleCommandSender.proxy(proxy: String) {
        logger.info(PixivHelperData.config.proxy + " -> " +  proxy)
        PixivHelperData.config.proxy = proxy
    }

    /**
     * 获取助手信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.info() = getHelper().runCatching {
        (authInfo ?: throw NotLoginException()).run {
            "账户：${user.account} \nPixivID: ${user.uid} \nToken: $refreshToken"
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

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
        login(username, password)
    }.onSuccess {
        quoteReply("${it.user.name} 登陆成功，Token ${it.refreshToken}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 登录 通过 Token
     * @param token refreshToken
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.refresh(
        token: String
    ) = getHelper().runCatching {
        refresh(token)
    }.onSuccess {
        quoteReply("${it.user.name} 登陆成功, Token ${it.refreshToken}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}