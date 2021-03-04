package xyz.cssxsh.pixiv.data

import net.mamoe.mirai.contact.*
import xyz.cssxsh.mirai.plugin.PixivHelper
import xyz.cssxsh.mirai.plugin.data.PixivConfigData
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class SimpleInfoDelegate(private val contact: Contact) : ReadWriteProperty<PixivHelper, Boolean> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
        PixivConfigData.isSimpleInfo[contact.toString()] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean =
        PixivConfigData.isSimpleInfo.getOrPut(contact.toString()) { contact !is Friend }

}