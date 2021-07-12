package xyz.cssxsh.mirai.plugin.command

import io.ktor.client.request.*
import kotlinx.serialization.decodeFromString
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.data.toPlainText
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.*
import java.io.File
import java.io.InputStream

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
                    useHttpClient { it.get<InputStream>(url) }.use { it.uploadAsImage(contact) }
                }.getOrElse {
                    logger.warning { "微博二维码下载失败 $it" }
                    url.toString().toPlainText()
                } + " 请扫码登录关联了Pixiv的微博".toPlainText()
            )
        }.let {
            "登陆成功，请妥善保管 RefreshToken: ${it.refreshToken}"
        }
    }

    @SubCommand
    @Description("登录 通过 Cookie")
    suspend fun CommandSenderOnMessage<*>.cookie() = withHelper {
        val json = File("cookie.json")
        sendMessage("加载 cookie 从 ${json.absolutePath}")
        cookie {
            PixivJson.decodeFromString<List<EditThisCookie>>(json.readText()).mapNotNull {
                it.runCatching { toCookie() }.getOrNull()
            }
        }.let {
            "登陆成功，请妥善保管 RefreshToken: ${it.refreshToken}"
        }
    }

    @SubCommand
    @Description("登录 通过 RefreshToken")
    suspend fun CommandSenderOnMessage<*>.refresh(token: String) = withHelper {
        config { refreshToken = token }
        refresh().let {
            "${it.user.name} 登陆成功 AccessToken: ${it.accessToken}, ExpiresTime: $expires"
        }
    }
}