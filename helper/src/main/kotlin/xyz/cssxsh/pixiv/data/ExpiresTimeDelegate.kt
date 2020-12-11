package xyz.cssxsh.pixiv.data

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import xyz.cssxsh.mirai.plugin.PixivHelper
import java.time.OffsetDateTime
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class ExpiresTimeDelegate(private val contact: Contact) : ReadWriteProperty<PixivHelper, OffsetDateTime> {

    private val now: OffsetDateTime get() = OffsetDateTime.now().withNano(0)

    private val userExpiresTimes: MutableMap<Long, OffsetDateTime> = mutableMapOf()

    private var defaultExpiresTime: OffsetDateTime = now

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: OffsetDateTime) = when (contact) {
        is User -> userExpiresTimes[contact.id] = value
        is Group -> defaultExpiresTime = value
        else -> throw IllegalAccessException("未知类型联系人!")
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): OffsetDateTime =when (contact) {
        is User -> userExpiresTimes.getOrPut(contact.id) { now }
        is Group -> defaultExpiresTime
        else -> throw IllegalAccessException("未知类型联系人!")
    }
}