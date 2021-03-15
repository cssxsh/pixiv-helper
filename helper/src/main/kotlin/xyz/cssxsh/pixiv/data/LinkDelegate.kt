package xyz.cssxsh.pixiv.data

import net.mamoe.mirai.contact.*
import xyz.cssxsh.mirai.plugin.PixivHelper
import xyz.cssxsh.mirai.plugin.data.PixivConfigData
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class LinkDelegate(private val contact: Contact) : ReadWriteProperty<PixivHelper, Boolean> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
        PixivConfigData.link[contact.toString()] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean =
        PixivConfigData.link.getOrPut(contact.toString()) { contact !is Group }

}