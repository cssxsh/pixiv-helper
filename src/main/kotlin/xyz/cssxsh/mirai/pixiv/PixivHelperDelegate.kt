package xyz.cssxsh.mirai.pixiv

import xyz.cssxsh.mirai.pixiv.data.*
import kotlin.properties.*
import kotlin.reflect.*

public object LinkDelegate : ReadWriteProperty<PixivHelper, Boolean> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
        PixivConfigData.link[thisRef.id] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean {
        return PixivConfigData.link.getOrDefault(thisRef.id, false)
    }
}

public object TagDelegate : ReadWriteProperty<PixivHelper, Boolean> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
        PixivConfigData.tag[thisRef.id] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean {
        return PixivConfigData.tag.getOrDefault(thisRef.id, false)
    }
}

public object AttrDelegate : ReadWriteProperty<PixivHelper, Boolean> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
        PixivConfigData.attr[thisRef.id] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean {
        return PixivConfigData.attr.getOrDefault(thisRef.id, false)
    }
}

public object MaxDelegate : ReadWriteProperty<PixivHelper, Int> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Int) {
        PixivConfigData.max[thisRef.id] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Int {
        return PixivConfigData.max.getOrDefault(thisRef.id, 3)
    }
}

public object ModelDelegate : ReadWriteProperty<PixivHelper, SendModel> {

    override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: SendModel) {
        PixivConfigData.model[thisRef.id] = value
    }

    override fun getValue(thisRef: PixivHelper, property: KProperty<*>): SendModel {
        return PixivConfigData.model.getOrDefault(thisRef.id, SendModel.Normal)
    }
}