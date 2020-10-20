package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import xyz.cssxsh.mirai.plugin.PixivHelper
import xyz.cssxsh.pixiv.client.PixivConfig
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class ConfigDelegate(private val contact: Contact) : ReadWriteProperty<PixivHelper, PixivConfig> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: PixivConfig) = when (contact) {
        is User -> PixivConfigData.configs[contact.id] = value
        is Group -> PixivConfigData.default = value
        else -> throw IllegalAccessException("未知类型联系人!")
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): PixivConfig = when (contact) {
        is User -> PixivConfigData.configs.getOrPut(contact.id) { PixivConfig() }
        is Group -> PixivConfigData.default
        else -> throw IllegalAccessException("未知类型联系人!")
    }
}