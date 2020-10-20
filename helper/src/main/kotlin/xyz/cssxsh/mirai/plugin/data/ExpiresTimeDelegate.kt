package xyz.cssxsh.mirai.plugin.data

import com.soywiz.klock.wrapped.WDateTimeTz
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import xyz.cssxsh.mirai.plugin.PixivHelper
import xyz.cssxsh.mirai.plugin.PixivHelperManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class ExpiresTimeDelegate(private val contact: Contact) : ReadWriteProperty<PixivHelper, WDateTimeTz> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: WDateTimeTz) = when (contact) {
        is User -> PixivHelperManager.userExpiresTimes[contact.id] = value
        is Group -> PixivHelperManager.defaultExpiresTimes = value
        else -> throw IllegalAccessException("未知类型联系人!")
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): WDateTimeTz =when (contact) {
        is User -> PixivHelperManager.userExpiresTimes.getOrPut(contact.id) { WDateTimeTz.nowLocal() }
        is Group -> PixivHelperManager.defaultExpiresTimes
        else -> throw IllegalAccessException("未知类型联系人!")
    }
}