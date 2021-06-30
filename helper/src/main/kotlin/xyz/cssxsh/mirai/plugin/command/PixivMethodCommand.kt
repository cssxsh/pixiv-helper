package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import xyz.cssxsh.mirai.plugin.*

object PixivMethodCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "pixiv",
    description = "PIXIV基本方法",
    overrideContext = PixivCommandArgumentContext
) {

    @SubCommand
    @Description("登录 通过 用户名，密码")
    suspend fun CommandSenderOnMessage<*>.login(username: String, password: String) = withHelper {
        login(username, password).let {
            "${it.user.name} 登陆成功，Token ${it.accessToken}, ExpiresTime: $expiresTime"
        }
    }

    @SubCommand
    @Description("登录 通过 Token")
    suspend fun CommandSenderOnMessage<*>.refresh(token: String) = withHelper {
        refresh(token).let {
            "${it.user.name} 登陆成功，Token ${it.accessToken}, ExpiresTime: $expiresTime"
        }
    }
}