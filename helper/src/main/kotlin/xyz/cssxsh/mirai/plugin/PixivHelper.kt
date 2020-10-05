@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.MiraiLogger
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.client.*
import xyz.cssxsh.pixiv.data.AuthResult

/**
 * 助手实例
 */
class PixivHelper(val contact: Contact, ) : SimplePixivClient(
    parentCoroutineContext = PixivHelperPlugin.coroutineContext,
    config = PixivHelperConfigData[contact].copy(proxy = PixivHelperSettings.proxy)
) {

    init {
        (config.refreshToken ?: authInfo?.refreshToken)?.let {
            runBlocking {
                runCatching {
                    authInfo = refresh(it)
                    config = config.copy(refreshToken = it)
                }.onSuccess {
                    logger.info("${contact}的助手自动${requireNotNull(authInfo).user.name}登陆成功")
                }.onFailure { ree ->
                    logger.info("${contact}的助手自动登陆失败, ${ree.message}")
                }
            }
        }
    }

    override var config: PixivConfig
        get() = PixivHelperConfigData[contact].copy(proxy = PixivHelperSettings.proxy)
        set(value) { PixivHelperConfigData[contact] = value }

    private val logger: MiraiLogger
        get() = PixivHelperPlugin.logger

    override var authInfo: AuthResult.AuthInfo?
        get() = PixivAuthInfoData.findByConfig(config)
        set(value) {
            if (value != null) {
                PixivAuthInfoData.authData[value.user.uid] = value
            }
        }

    val isLoggedIn: Boolean
        get() = authInfo != null

    override fun config(block: PixivConfig.() -> Unit) =
        config.apply(block).also { PixivHelperConfigData[contact] = it }

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