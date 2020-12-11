package xyz.cssxsh.pixiv.data

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import xyz.cssxsh.mirai.plugin.PixivHelper
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class AuthInfoDelegate(private val contact: Contact) : ReadWriteProperty<PixivHelper, AuthResult.AuthInfo?> {

    private val userAuthInfos: MutableMap<Long, AuthResult.AuthInfo?> = mutableMapOf()

    private var defaultAuthInfos: AuthResult.AuthInfo? = null

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: AuthResult.AuthInfo?) = when (contact) {
        is User -> userAuthInfos[contact.id] = value
        is Group -> defaultAuthInfos = value
        else -> throw IllegalAccessException("未知类型联系人!")
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): AuthResult.AuthInfo? = when (contact) {
        is User -> userAuthInfos[contact.id]
        is Group -> defaultAuthInfos
        else -> throw IllegalAccessException("未知类型联系人!")
    }
}