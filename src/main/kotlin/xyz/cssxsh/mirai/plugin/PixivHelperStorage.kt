@file:Suppress("unused")
package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.message.data.Message

object PixivHelperStorage {
    /**
     * 用于存储和用户关联的助手
     */
    val users = HashMap<Long, PixivHelper>()

    /**
     * 用于存储和群关联的助手
     */
    val groups = HashMap<Long, PixivHelper>()

    /**
     * 通过联系人获取对应的助手
     */
    fun getOrPut(contact: Contact, defaultValue: () -> PixivHelper) = when(contact) {
        is User -> users.getOrPut(contact.id, defaultValue)
        is Group -> groups.getOrPut(contact.id, defaultValue)
        else -> throw Exception("未知类型联系人!")
    }

    /**
     * 发送消息到所有用户
     * @return 消息回执列表
     */
    suspend fun sendMessageToUsers(message: Message) = users.mapValues { it.value.contact.sendMessage(message) }

    /**
     * 发送消息到所有用户
     * @return 消息回执列表
     */
    suspend fun sendMessageToUsers(message: String) = users.mapValues { it.value.contact.sendMessage(message) }

    /**
     * 发送消息到所有群
     * @return 消息回执列表
     */
    suspend fun sendMessageToGroups(message: Message) = groups.mapValues { it.value.contact.sendMessage(message) }


    /**
     * 发送消息到所有群
     * @return 消息回执列表
     */
    suspend fun sendMessageToGroups(message: String) = groups.mapValues { it.value.contact.sendMessage(message) }

    /**
     * 发送消息到全部联系人
     * @return 消息回执列表
     */
    suspend fun sendMessageToAll(message: Message) = sendMessageToUsers(message) + sendMessageToGroups(message)

    /**
     * 发送消息到全部联系人
     * @return 消息回执列表
     */
    suspend fun sendMessageToAll(message: String) = sendMessageToUsers(message) + sendMessageToGroups(message)
}