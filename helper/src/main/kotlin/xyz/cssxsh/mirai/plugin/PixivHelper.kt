@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin

import com.soywiz.klock.DateFormat
import com.soywiz.klock.KlockLocale
import com.soywiz.klock.PatternDateFormat
import com.soywiz.klock.TimezoneNames
import com.soywiz.klock.locale.chinese
import com.soywiz.klock.wrapped.WDateTime
import com.soywiz.klock.wrapped.WDateTimeTz
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
    coroutineName = "PixivHelper:${contact}",
    config = PixivConfigData.config
), PixivHelperLogger {

    companion object {
        val DATE_FORMAT_CHINESE = PatternDateFormat("YYYY-MM-dd HH:mm:ss", KlockLocale.chinese)
    }

    override var config: PixivConfig
        get() = PixivConfigData.config
        set(value) { PixivConfigData.config = value }

    override var authInfo: AuthResult.AuthInfo?
        get() = PixivHelperManager.authInfo
        set(value) { PixivHelperManager.authInfo = value }

    public override var expiresTime: WDateTimeTz
        get() = PixivHelperManager.expiresTime
        set(value) { PixivHelperManager.expiresTime = value }

    val historyQueue by lazy {
        ArrayBlockingQueue<Long>(PixivHelperSettings.minInterval)
    }

    var minSanityLevel = 1
        set(value) { field = minOf(value,6) }

    var minBookmarks: Long = 0

    var simpleInfo: Boolean
        get() = PixivConfigData.simpleInfo.getOrPut(contact.id, { true })
        set(value) {
            PixivConfigData.simpleInfo[contact.id] = value
        }

    override fun config(block: PixivConfig.() -> Unit) =
        config.apply(block).also { PixivConfigData.config = it }

    override suspend fun refresh(token: String) = super.refresh(token).also {
        logger.info("$it by RefreshToken: $token, expiresTime: ${expiresTime.format(DATE_FORMAT_CHINESE)}")
    }

    override suspend fun login(mailOrPixivID: String, password: String) = super.login(mailOrPixivID, password).also {
        logger.info("$it by Account: $mailOrPixivID, expiresTime: ${expiresTime.format(DATE_FORMAT_CHINESE)}")
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