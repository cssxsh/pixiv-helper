package xyz.cssxsh.mirai.pixiv.command

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.selenium.*
import java.io.*

public object PixivMethodCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "pixiv",
    description = "PIXIV基本方法"
), PixivHelperCommand {

    @SubCommand
    @Description("登录 通过 登录关联的微博")
    public suspend fun CommandSender.sina(): Unit = PixivClientPool.auth { pixiv ->
        when (this) {
            is UserCommandSender -> {
                val auth = pixiv.sina { url ->
                    val qrcode = try {
                        pixiv.useHttpClient { it.get(url).readBytes() }
                            .toExternalResource()
                            .use { it.uploadAsImage(subject) }
                    } catch (cause: Throwable) {
                        logger.warning({ "微博二维码下载失败" }, cause)
                        url.toString().toPlainText()
                    }
                    sendMessage(message = qrcode + " 请扫码登录关联了Pixiv的微博".toPlainText())
                }

                sendMessage("账户 ${auth.user.name}#${auth.user.uid} 登陆成功")

                logger.info { "账户 ${auth.user.name}#${auth.user.uid} 登陆成功，请妥善保管 RefreshToken: ${auth.refreshToken}" }
            }
            is ConsoleCommandSender -> {
                val auth = pixiv.sina { url ->
                    sendMessage(message = "$url  请扫码登录关联了Pixiv的微博")
                }

                sendMessage("账户 ${auth.user.name}#${auth.user.uid} 登陆成功，请妥善保管 RefreshToken: ${auth.refreshToken}")
            }
        }
    }

    @SubCommand
    @Description("登录 通过 Cookie")
    public suspend fun CommandSender.cookie(): Unit = PixivClientPool.auth { pixiv ->
        val json = File("cookie.json")
        sendMessage("加载 cookie 从 ${json.absolutePath}")
        val auth = pixiv.cookie {
            @OptIn(ExperimentalSerializationApi::class)
            PixivJson.decodeFromString<List<EditThisCookie>>(json.readText()).map { it.toCookie() }
        }

        if (subject is Group) {
            sendMessage("账户 ${auth.user.name}#${auth.user.uid} 登陆成功")
            logger.info { "账户 ${auth.user.name}#${auth.user.uid} 登陆成功，请妥善保管 RefreshToken: ${auth.refreshToken}" }
        } else {
            sendMessage("账户 ${auth.user.name}#${auth.user.uid} 登陆成功，请妥善保管 RefreshToken: ${auth.refreshToken}")
        }
    }

    @SubCommand
    @Description("登录 通过 浏览器登录")
    public suspend fun CommandSender.selenium(): Unit = PixivClientPool.auth { pixiv ->
        val config = if (ProxyApi.isNotBlank()) {
            sendMessage("发现 pixiv-helper 配置的代理 ，将会配置给浏览器")
            object : RemoteWebDriverConfig {
                override val headless: Boolean = false
                override val proxy: String = ProxyApi
            }
        } else {
            sendMessage("浏览器将使用默认的代理配置，或者你可以在浏览器启动后更改配置")
            object : RemoteWebDriverConfig {
                override val headless: Boolean = false
            }
        }

        val auth = useRemoteWebDriver(config) { driver ->
            pixiv.selenium(driver = driver, timeout = 900_000)
        }

        if (subject is Group) {
            sendMessage("账户 ${auth.user.name}#${auth.user.uid} 登陆成功")
            logger.info { "账户 ${auth.user.name}#${auth.user.uid} 登陆成功，请妥善保管 RefreshToken: ${auth.refreshToken}" }
        } else {
            sendMessage("账户 ${auth.user.name}#${auth.user.uid} 登陆成功，请妥善保管 RefreshToken: ${auth.refreshToken}")
        }
    }

    @SubCommand
    @Description("登录 通过 RefreshToken")
    public suspend fun CommandSender.refresh(token: String): Unit = PixivClientPool.auth { pixiv ->
        pixiv.config { refreshToken = token }
        val auth = pixiv.refresh()

        sendMessage("账户 ${auth.user.name}#${auth.user.uid} 登陆成功")
    }

    @SubCommand
    @Description("绑定 Pixiv 账户 作为上下文")
    public suspend fun CommandSender.bind(uid: Long, contact: Contact? = subject) {
        if (contact == null) {
            sendMessage("需要指定绑定的 用户/群")
            return
        }
        PixivClientPool.bind(uid = uid, subject = contact.id)
        sendMessage("绑定已添加")
    }

    @SubCommand
    @Description("账户池详情")
    public suspend fun CommandSender.pool() {
        val message = buildMessageChain {
            appendLine("clients")
            for ((_, client) in PixivClientPool.clients) {
                val auth = client.auth ?: continue
                appendLine("User: ${auth.user.uid}")
                appendLine("Name: ${auth.user.name}")
                appendLine("Account: ${auth.user.account}")
                appendLine("Premium: ${auth.user.isPremium}")
                appendLine("AccessToken: ${auth.accessToken}")
                appendLine("RefreshToken: ${auth.refreshToken}")
            }
            appendLine("binded")
            for ((user, pixiv) in PixivClientPool.binded) {
                appendLine("$user - $pixiv")
            }
            appendLine("console")
            appendLine("${PixivClientPool.default}")
        }

        sendMessage(message = message)
    }
}