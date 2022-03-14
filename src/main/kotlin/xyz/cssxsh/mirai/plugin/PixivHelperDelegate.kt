package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.auth.*
import java.time.*
import kotlin.properties.*
import kotlin.reflect.*

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
            is User -> PixivConfigData.tokens[thisRef.contact.id] ?: PixivConfigData.default
            is Group -> PixivConfigData.default
            else -> throw IllegalAccessException("未知类型联系人!")
        }
        return DEFAULT_PIXIV_CONFIG.copy(proxy = ProxyApi, refreshToken = token)
    }
}

object AuthResultDelegate : ReadWriteProperty<PixivHelper, AuthResult?> {

    private val UserAuthInfos: MutableMap<Long, AuthResult?> = HashMap()

    private var DefaultAuthInfo: AuthResult? = null

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: AuthResult?) {
        when (thisRef.contact) {
            is User -> UserAuthInfos[thisRef.contact.id] = value
            is Group -> DefaultAuthInfo = value
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): AuthResult? {
        return when (thisRef.contact) {
            is User -> UserAuthInfos[thisRef.contact.id] ?: DefaultAuthInfo
            is Group -> DefaultAuthInfo
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }
}

object ExpiresTimeDelegate : ReadWriteProperty<PixivHelper, OffsetDateTime> {

    private val UserExpiresTimes: MutableMap<Long, OffsetDateTime> = HashMap()

    private var DefaultExpiresTime: OffsetDateTime = OffsetDateTime.MIN

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: OffsetDateTime) {
        when (thisRef.contact) {
            is User -> UserExpiresTimes[thisRef.contact.id] = value
            is Group -> DefaultExpiresTime = value
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): OffsetDateTime {
        return when (thisRef.contact) {
            is User -> UserExpiresTimes.getOrPut(thisRef.contact.id) { OffsetDateTime.MIN }
            is Group -> DefaultExpiresTime
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }
}

object MutexDelegate : ReadOnlyProperty<PixivHelper, Mutex> {

    private val UserMutexes: MutableMap<Long, Mutex> = HashMap()

    private val DefaultMutex: Mutex = Mutex()

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Mutex {
        return when (thisRef.contact) {
            is User -> UserMutexes.getOrPut(thisRef.contact.id, ::Mutex)
            is Group -> DefaultMutex
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }
}

object LinkDelegate : ReadWriteProperty<PixivHelper, Boolean> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
        PixivConfigData.link[thisRef.contact.toString()] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean {
        return PixivConfigData.link.getOrPut(thisRef.contact.toString()) {
            thisRef.contact !is Group
        }
    }
}

object TagDelegate : ReadWriteProperty<PixivHelper, Boolean> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
        PixivConfigData.tag[thisRef.contact.toString()] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean {
        return PixivConfigData.tag.getOrPut(thisRef.contact.toString()) {
            thisRef.contact !is Group
        }
    }
}

object AttrDelegate : ReadWriteProperty<PixivHelper, Boolean> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
        PixivConfigData.attr[thisRef.contact.toString()] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean {
        return PixivConfigData.attr.getOrPut(thisRef.contact.toString()) {
            thisRef.contact !is Group
        }
    }
}

object MaxDelegate : ReadWriteProperty<PixivHelper, Int> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Int) {
        PixivConfigData.max[thisRef.contact.toString()] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Int {
        return PixivConfigData.max.getOrPut(thisRef.contact.toString()) { 3 }
    }
}

object ModelDelegate : ReadWriteProperty<PixivHelper, SendModel> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: SendModel) {
        PixivConfigData.model[thisRef.contact.toString()] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): SendModel {
        return PixivConfigData.model.getOrPut(thisRef.contact.toString()) { SendModel.Normal }
    }
}

private val helpers = HashMap<Contact, PixivHelper>()

private val iterator: Iterator<PixivAuthClient> = iterator { while (abilities.isNotEmpty()) yieldAll(abilities) }

internal val abilities get() = helpers.values.distinctBy { it.uid }.filter { it.uid != null }

/**
 * random get authed helper at [helpers]
 */
internal fun PixivAuthClient(): PixivAuthClient = synchronized(iterator) { iterator.next() }

internal val Contact.helper by ReadOnlyProperty<Contact, PixivHelper> { contact, _ ->
    helpers.getOrPut(contact) {
        PixivHelper(contact).apply {
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

internal val UserCommandSender.helper get() = subject.helper

class PixivHelperDelegate<T>(private val default: (Contact) -> T) : ReadWriteProperty<PixivHelper, T> {

    private val map: MutableMap<Contact, T> = HashMap()

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: T) {
        map[thisRef.contact] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): T {
        return map.getOrPut(thisRef.contact) { default(thisRef.contact) }
    }
}