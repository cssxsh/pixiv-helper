@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.client.*
import xyz.cssxsh.pixiv.data.AuthResult
import java.util.concurrent.ArrayBlockingQueue
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

/**
 * 助手实例
 */
@ExperimentalTime
class PixivHelper(val contact: Contact) : SimplePixivClient(
    parentCoroutineContext = PixivHelperPlugin.coroutineContext,
    config = PixivConfigData.config
), PixivHelperLogger {

    init {
        if (isLoggedIn.not()) {
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
                    logger.info("${contact}的助手自动${user.name}登陆成功, ")
                    flushTaken.start()
                }
            }.onFailure {
                logger.info("${contact}的助手自动登陆失败", it)
            }
        }
    }

    private var flushTaken: Job = launch(start = CoroutineStart.LAZY) {
        delay(getAuthInfoOrThrow().expiresIn.seconds)
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

    val isLoggedIn: Boolean
        get() = authInfo != null

    override fun config(block: PixivConfig.() -> Unit) =
        config.apply(block).also { PixivConfigData.config = it }

    override suspend fun refresh(): AuthResult.AuthInfo =
        super.refresh().also { authInfo = it }

    override suspend fun refresh(token: String): AuthResult.AuthInfo =
        super.refresh(token).also { logger.info("$it by RefreshToken: $token") }

    override suspend fun login(): AuthResult.AuthInfo =
        super.login().also { authInfo = it }

    override suspend fun login(mailOrPixivID: String, password: String): AuthResult.AuthInfo =
        super.login(mailOrPixivID, password).also { logger.info("$it by Account: $mailOrPixivID") }

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