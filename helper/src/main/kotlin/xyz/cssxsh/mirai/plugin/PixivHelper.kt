@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
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

    var minSanityLevel = 1
        set(value) { field = minOf(value,6) }

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