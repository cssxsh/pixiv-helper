package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.contact.Group

object PixivHelperPluginData : AutoSavePluginData() {
    /**
     * 用于存储用户关联
     */
    private val users: MutableMap<Long, PixivClientData> by value(mutableMapOf())

    /**
     * 用于存储群关联
     */
    private val groups: MutableMap<Long, PixivClientData> by value(mutableMapOf())

    /**
     * 通过联系人获取
     * @param contact 助手的联系人
     * @param defaultValue 构建默认值
     */
    private fun getOrPut(contact: Contact, defaultValue: () -> PixivClientData): PixivClientData = when(contact) {
        is User -> users.getOrPut(contact.id) { defaultValue() }
        is Group -> groups.getOrPut(contact.id) { defaultValue() }
        else -> throw IllegalArgumentException("未知类型联系人!")
    }

    /**
     * 操作符[] 关联 getOrPut, 默认值为 PixivClientData()
     * @see [getOrPut]
     */
    operator fun get(contact: Contact): PixivClientData = getOrPut(contact) { PixivClientData() }

    /**
     * set 操作符[] 关联 put
     */
    operator fun set(contact: Contact, value: PixivClientData) = when(contact) {
        is User -> users[contact.id] = value
        is Group -> groups[contact.id] = value
        else -> throw IllegalArgumentException("未知类型联系人!")
    }
}