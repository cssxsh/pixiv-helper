@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.MiraiLogger
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.GrantType
import xyz.cssxsh.pixiv.client.*
import xyz.cssxsh.pixiv.data.AuthResult

/**
 * 助手实例
 */
class PixivHelper(
    val contact: Contact,
) : SimplePixivClient(PixivHelperPlugin.coroutineContext, PixivHelperPluginData[contact].config) {

    init {
        (config.refreshToken ?: authInfo?.refreshToken)?.let {
            runBlocking {
                runCatching {
                    data.copy(authInfo = refresh(it), config = config.copy(refreshToken = it))
                }.onSuccess {
                    logger.info("${contact}的助手自动${requireNotNull(authInfo).user.name}登陆成功")
                }.onFailure {
                    logger.info("${contact}的助手自动${requireNotNull(authInfo).user.name}登陆失败")
                }
            }
        }
    }

    private val logger: MiraiLogger
        get() = PixivHelperPlugin.logger

    private var data: PixivClientData
        get() = PixivHelperPluginData[contact]
        set(value) { PixivHelperPluginData[contact] = value }

    override var authInfo: AuthResult.AuthInfo?
        get() = data.authInfo
        set(value) { data = data.copy(authInfo = value) }

    val isLoggedIn: Boolean
        get() = data.authInfo != null

    override val config: PixivConfig
        get() = data.config

    override fun config(block: PixivConfig.() -> Unit) =
        config.apply(block).also { data = data.copy(config = it) }

    suspend fun refresh(): AuthResult.AuthInfo =
        super.auth(GrantType.REFRESH_TOKEN, config).also { authInfo = it }

    override suspend fun refresh(token: String): AuthResult.AuthInfo =
        super.refresh(token).also { logger.info("Auth by RefreshToken: $token") }

    suspend fun login(): AuthResult.AuthInfo =
        super.auth(GrantType.PASSWORD, config).also { authInfo = it }

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