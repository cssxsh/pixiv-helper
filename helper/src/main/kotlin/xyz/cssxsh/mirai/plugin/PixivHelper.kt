@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.secondsToMillis
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.client.*
import xyz.cssxsh.pixiv.data.AuthResult
import java.util.concurrent.ArrayBlockingQueue

/**
 * 助手实例
 */
class PixivHelper(val contact: Contact) : SimplePixivClient(
    parentCoroutineContext = PixivHelperPlugin.coroutineContext,
    config = PixivConfigData.config
), PixivHelperLogger {

    init {
        if (authInfo == null) {
            runBlocking {
                runCatching {
                    config.refreshToken?.let { token ->
                        authInfo = refresh(token)
                    } ?: config.account?.let { account ->
                        // XXX login(account)
                        authInfo = login(account.mailOrUID, account.password)
                    }
                }
            }.onSuccess {
                authInfo?.run {
                    config = config.copy(refreshToken = refreshToken)
                    logger.info("${contact}的助手自动${user.name}登陆成功")
                }
            }.onFailure {
                logger.info("${contact}的助手自动登陆失败", it)
            }
        }
    }

    private val flushTaken: Job = launch(start = CoroutineStart.LAZY) {
        delay(getAuthInfoOrThrow().expiresIn.secondsToMillis)
        logger.info("Token将刷新")
        refresh()
    }

    override var config: PixivConfig
        get() = PixivConfigData.config
        set(value) { PixivConfigData.config = value }

    override var authInfo: AuthResult.AuthInfo?
        get() = PixivHelperManager.authInfo
        set(value) {
            if (value != null) {
                PixivHelperManager.authInfo = value
            }
        }

    val historyQueue by lazy {
        ArrayBlockingQueue<Long>(PixivHelperSettings.minInterval)
    }

    var simpleInfo: Boolean
        get() = PixivConfigData.simpleInfo.getOrPut(contact.id, { true })
        set(value) {
            PixivConfigData.simpleInfo[contact.id] = value
        }

    override fun config(block: PixivConfig.() -> Unit) =
        config.apply(block).also { PixivConfigData.config = it }

    override suspend fun refresh(token: String) = super.refresh(token).also {
        logger.info("$it by RefreshToken: $token")
    }

    override suspend fun login(mailOrPixivID: String, password: String) = super.login(mailOrPixivID, password).also {
        logger.info("$it by Account: $mailOrPixivID")
    }

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