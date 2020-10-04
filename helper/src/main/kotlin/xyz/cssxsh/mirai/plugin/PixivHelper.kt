@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.MiraiLogger
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.client.*
import xyz.cssxsh.pixiv.client.exception.NotLoginException
import xyz.cssxsh.pixiv.data.AuthResult

/**
 * 助手实例
 */
class PixivHelper(
    val contact: Contact,
) : SimplePixivClient(PixivHelperPlugin.coroutineContext, PixivHelperPluginData[contact].config) {

    private val logger: MiraiLogger
        get() = PixivHelperPlugin.logger

    private var data: PixivClientData
        get() = PixivHelperPluginData[contact]
        set(value) { PixivHelperPluginData[contact] = value }

    override var authInfo: AuthResult.AuthInfo
        get() = data.authInfo ?: throw NotLoginException()
        set(value) { data = data.copy(authInfo = value) }

    override val isLoggedIn: Boolean
        get() = data.authInfo != null

    override val config: PixivConfig
        get() = data.config

    override fun config(block: PixivConfig.() -> Unit) =
        config.apply(block).also { data = data.copy(config = it) }

    override suspend fun refresh(): AuthResult.AuthInfo =
        super.refresh().also { authInfo = it }

    override suspend fun refresh(token: String): AuthResult.AuthInfo =
        super.refresh(token).also { logger.info("Auth by RefreshToken: $token") }

    override suspend fun login(): AuthResult.AuthInfo =
        super.login().also { authInfo = it }

    override suspend fun login(mailOrPixivID: String, password: String): AuthResult.AuthInfo =
        super.login(mailOrPixivID, password).also { logger.info("Auth by Account: $mailOrPixivID") }


    /**
     * 给这个助手的联系人发送消息
     */
    @JvmSynthetic
    suspend fun reply(message: Message): MessageReceipt<Contact> =
        contact.sendMessage(message)

    /**
     * 给这个助手的联系人发送文本消息
     */
    @JvmSynthetic
    suspend fun reply(plain: String): MessageReceipt<Contact> =
        contact.sendMessage(PlainText(plain))
}