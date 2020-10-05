package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.contact.Group

object PixivHelperPluginData : AutoSavePluginData() {
    /**
     * 用于存储用户关联助手ID
     */
    private val users: MutableMap<Long, Long> by value(mutableMapOf())

    /**
     * 用于存储群关联
     */
    private val groups: MutableMap<Long, Long> by value(mutableMapOf())


    /**
     * 用于存储群关联
     */
    private val clientData: MutableMap<Long, PixivClientData> by value(mutableMapOf())

    /**
     * 通过联系人获取
     * @param contact 助手的联系人
     * @param defaultValue 构建默认值
     */
    private fun getOrPut(contact: Contact, defaultValue: () -> PixivClientData): PixivClientData = when(contact) {
        is User -> users[contact.id]?.let { clientData[it] }
        is Group -> groups[contact.id]?.let { clientData[it] }
        else -> throw IllegalArgumentException("未知类型联系人!")
    } ?: set(contact, defaultValue())

    /**
     * 操作符[] 关联 getOrPut, 默认值为 PixivClientData()
     * @see [getOrPut]
     */
    operator fun get(contact: Contact): PixivClientData = getOrPut(contact) { PixivClientData() }

    /**
     * set 操作符[] 关联 put
     */
    operator fun set(contact: Contact, value: PixivClientData) = value.apply {
        authInfo?.user?.uid?.let {
            when(contact) {
                is User -> users[contact.id] = it
                is Group -> groups[contact.id] = it
                else -> throw IllegalArgumentException("未知类型联系人!")
            }
            clientData[it] = this
        }
    }
}