package xyz.cssxsh.pixiv.data

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import xyz.cssxsh.mirai.plugin.PixivHelper
import xyz.cssxsh.mirai.plugin.PixivHelperManager
import java.time.OffsetDateTime
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class ExpiresTimeDelegate(private val contact: Contact) : ReadWriteProperty<PixivHelper, OffsetDateTime> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: OffsetDateTime) = when (contact) {
        is User -> PixivHelperManager.userExpiresTimes[contact.id] = value
        is Group -> PixivHelperManager.defaultExpiresTime = value
        else -> throw IllegalAccessException("未知类型联系人!")
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): OffsetDateTime =when (contact) {
        is User -> PixivHelperManager.userExpiresTimes.getOrPut(contact.id) { OffsetDateTime.now() }
        is Group -> PixivHelperManager.defaultExpiresTime
        else -> throw IllegalAccessException("未知类型联系人!")
    }
}