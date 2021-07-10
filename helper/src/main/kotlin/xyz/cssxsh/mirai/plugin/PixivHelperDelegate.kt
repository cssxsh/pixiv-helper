package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.contact.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.PixivConfig
import xyz.cssxsh.pixiv.auth.AuthResult
import java.time.OffsetDateTime
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val UserAuthInfos: MutableMap<Long, AuthResult?> = mutableMapOf()

private var DefaultAuthInfos: AuthResult? = null

private val now: () -> OffsetDateTime = { OffsetDateTime.now().withNano(0) }

private val UserExpiresTimes: MutableMap<Long, OffsetDateTime> = mutableMapOf()

private var DefaultExpiresTime: OffsetDateTime = now()

private val UserMutex: MutableMap<Long, Mutex> = mutableMapOf()

private var DefaultMutex: Mutex = Mutex()

object ConfigDelegate : ReadWriteProperty<PixivHelper, PixivConfig> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: PixivConfig) {
        val token = value.refreshToken.orEmpty()
        when (thisRef.contact) {
            is User -> PixivConfigData.tokens[thisRef.contact.id] = token
            is Group -> PixivConfigData.default = token
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): PixivConfig {
        val token = when (thisRef.contact) {
            is User -> PixivConfigData.tokens[thisRef.contact.id].orEmpty()
            is Group -> PixivConfigData.default
            else -> throw IllegalAccessException("未知类型联系人!")
        }
        return DEFAULT_PIXIV_CONFIG.copy(proxy = PixivHelperSettings.proxy, refreshToken = token)
    }
}

object AuthResultDelegate : ReadWriteProperty<PixivHelper, AuthResult?> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: AuthResult?) {
        when (thisRef.contact) {
            is User -> UserAuthInfos[thisRef.contact.id] = value
            is Group -> DefaultAuthInfos = value
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): AuthResult? = when (thisRef.contact) {
        is User -> UserAuthInfos[thisRef.contact.id]
        is Group -> DefaultAuthInfos
        else -> throw IllegalAccessException("未知类型联系人!")
    }
}

object ExpiresTimeDelegate : ReadWriteProperty<PixivHelper, OffsetDateTime> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: OffsetDateTime) {
        when (thisRef.contact) {
            is User -> UserExpiresTimes[thisRef.contact.id] = value
            is Group -> DefaultExpiresTime = value
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): OffsetDateTime = when (thisRef.contact) {
        is User -> UserExpiresTimes.getOrPut(thisRef.contact.id, now)
        is Group -> DefaultExpiresTime
        else -> throw IllegalAccessException("未知类型联系人!")
    }
}

object MutexDelegate : ReadWriteProperty<PixivHelper, Mutex> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Mutex) {
        when (thisRef.contact) {
            is User -> UserMutex[thisRef.contact.id] = value
            is Group -> DefaultMutex = value
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Mutex = when (thisRef.contact) {
        is User -> UserMutex.getOrPut(thisRef.contact.id, ::Mutex)
        is Group -> DefaultMutex
        else -> throw IllegalAccessException("未知类型联系人!")
    }
}

object LinkDelegate : ReadWriteProperty<PixivHelper, Boolean> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
        PixivConfigData.link[thisRef.contact.toString()] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean =
        PixivConfigData.link.getOrPut(thisRef.contact.toString()) { thisRef.contact !is Group }

}

object TagDelegate : ReadWriteProperty<PixivHelper, Boolean> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
        PixivConfigData.tag[thisRef.contact.toString()] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean =
        PixivConfigData.tag.getOrPut(thisRef.contact.toString()) { thisRef.contact !is Group }

}

object AttrDelegate : ReadWriteProperty<PixivHelper, Boolean> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
        PixivConfigData.attr[thisRef.contact.toString()] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean =
        PixivConfigData.attr.getOrPut(thisRef.contact.toString()) { thisRef.contact !is Group }

}

object MaxDelegate : ReadWriteProperty<PixivHelper, Int> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Int) {
        PixivConfigData.max[thisRef.contact.toString()] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Int =
        PixivConfigData.max.getOrPut(thisRef.contact.toString()) { 3 }

}

private val helpers = mutableMapOf<Contact, PixivHelper>()

internal fun Contact.getHelper(): PixivHelper {
    return helpers.getOrPut(this) {
        PixivHelper(this).apply {
            if (contact is User && config.refreshToken.isNullOrBlank()) {
                launch {
                    send {
                        "私聊模式中 将会独立关联账户，请使用 /pixiv 指令尝试登陆"
                    }
                }
            }
        }
    }
}

internal val CommandSenderOnMessage<*>.helper by ReadOnlyProperty<CommandSenderOnMessage<*>, PixivHelper> { sender, _ ->
    sender.fromEvent.subject.getHelper()
}

class PixivHelperDelegate<T>(private val default: (Contact) -> T) : ReadWriteProperty<PixivHelper, T> {

    private val map: MutableMap<Contact, T> = mutableMapOf()

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: T) {
        map[thisRef.contact] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): T {
        return map.getOrPut(thisRef.contact) { default(thisRef.contact) }
    }
}