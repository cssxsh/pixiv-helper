package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.data.toPlainText
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.pixiv.*

object PixivMethodCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "pixiv",
    description = "PIXIV基本方法",
    overrideContext = PixivCommandArgumentContext
) {

    @SubCommand
    @Description("登录 通过 登录关联的微博")
    suspend fun CommandSenderOnMessage<*>.sina() = withHelper {
        sina { url ->
            sendMessage(
                runCatching {
                    PixivHelperDownloader.downloadImage(url).inputStream().uploadAsImage(contact)
                }.getOrElse {
                    url.toString().toPlainText()
                } + " 请扫码登录关联了Pixiv的微博".toPlainText()
            )
        }.let {
            "登陆成功，请妥善保管 RefreshToken: ${it.refreshToken}"
        }
    }

    @SubCommand
    @Description("登录 通过 RefreshToken")
    suspend fun CommandSenderOnMessage<*>.refresh(token: String) = withHelper {
        refresh(token).let {
            "${it.user.name} 登陆成功 AccessToken: ${it.accessToken}, ExpiresTime: $expires"
        }
    }
}