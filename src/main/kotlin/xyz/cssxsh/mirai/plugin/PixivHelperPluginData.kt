package xyz.cssxsh.mirai.plugin

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.getValue
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.contact.Group
import xyz.cssxsh.mirai.plugin.data.PixivClientData

object PixivHelperPluginData : AutoSavePluginData() {
    /**
     * 用于存储用户关联
     */
    private val users: MutableMap<Long, String> by value(mutableMapOf())

    /**
     * 用于存储群关联
     */
    private val groups: MutableMap<Long, String> by value(mutableMapOf())

    fun usersForEach(block: (Long, PixivClientData) -> Unit) = users.forEach { block(it.key, it.value.toPixivClientSetting()) }

    fun groupsForEach(block: (Long, PixivClientData) -> Unit) = users.forEach { block(it.key, it.value.toPixivClientSetting()) }

    /**
     * 通过联系人获取
     * @param contact 助手的联系人
     * @param defaultValue 构建默认值
     */
    fun getOrPut(contact: Contact, defaultValue: () -> PixivClientData): PixivClientData = when(contact) {
        is User -> users.getOrPut(contact.id) { defaultValue().toJson() } .toPixivClientSetting()
        is Group -> groups.getOrPut(contact.id) { defaultValue().toJson() } .toPixivClientSetting()
        else -> throw IllegalAccessException("未知类型联系人!")
    }

    /**
     * 操作符[] 关联 getOrPut, 默认值为 PixivClientData()
     * @see [getOrPut]
     */
    operator fun get(contact: Contact): PixivClientData = getOrPut(contact, ::PixivClientData)

    /**
     * set 操作符[] 关联 put
     */
    operator fun set(contact: Contact, value: PixivClientData) = when(contact) {
        is User -> users[contact.id] = value.toJson()
        is Group -> groups[contact.id] =  value.toJson()
        else -> throw IllegalAccessException("未知类型联系人!")
    }

    /**
     * 序列化
     */
    private fun PixivClientData.toJson(): String = Json.encodeToString(this)

    /**
     * 反序列化
     */
    private fun String.toPixivClientSetting(): PixivClientData =
        Json.decodeFromString(PixivClientData.serializer(), this)
}