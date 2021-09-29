package xyz.cssxsh.mirai.plugin.command

import io.ktor.client.request.*
import kotlinx.serialization.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.*
import java.io.*

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
            logger.info { "微博 登录二维码  $url" }
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
            @OptIn(ExperimentalSerializationApi::class)
            PixivJson.decodeFromString<List<EditThisCookie>>(json.readText()).map { it.toCookie() }
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