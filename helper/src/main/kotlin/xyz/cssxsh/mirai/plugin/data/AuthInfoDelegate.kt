package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import xyz.cssxsh.mirai.plugin.PixivHelper
import xyz.cssxsh.mirai.plugin.PixivHelperManager
import xyz.cssxsh.pixiv.data.AuthResult
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class AuthInfoDelegate(private val contact: Contact) : ReadWriteProperty<PixivHelper, AuthResult.AuthInfo?> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: AuthResult.AuthInfo?) = when (contact) {
        is User -> PixivHelperManager.userAuthInfos[contact.id] = value
        is Group -> PixivHelperManager.defaultAuthInfos = value
        else -> throw IllegalAccessException("未知类型联系人!")
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): AuthResult.AuthInfo? = when (contact) {
        is User -> PixivHelperManager.userAuthInfos[contact.id]
        is Group -> PixivHelperManager.defaultAuthInfos
        else -> throw IllegalAccessException("未知类型联系人!")
    }
}