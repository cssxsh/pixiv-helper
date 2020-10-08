package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import xyz.cssxsh.pixiv.data.AuthResult

object PixivHelperManager {
    /**
     * 用于存储用户关联的助手
     */
    private val users: MutableMap<Long, PixivHelper> = mutableMapOf()

    /**
     * 用于存储群关联的助手
     */
    private val groups: MutableMap<Long, PixivHelper> = mutableMapOf()


    /**
     * 用于存储认证消息
     */
    var authInfo: AuthResult.AuthInfo? = null

    /**
     * 通过联系人获取
     * @param contact 助手的联系人
     * @param defaultValue 构建默认值
     */
    fun getOrPut(contact: Contact, defaultValue: () -> PixivHelper): PixivHelper = when(contact) {
        is User -> users.getOrPut(contact.id) { defaultValue() }
        is Group -> groups.getOrPut(contact.id) { defaultValue() }
        else -> throw IllegalAccessException("未知类型联系人!")
    }

    /**
     * 操作符[] 关联 getOrPut, 默认值为 PixivClientData()
     * @see [getOrPut]
     */
    operator fun get(contact: Contact): PixivHelper = getOrPut(contact) { PixivHelper(contact) }

    /**
     * set 操作符[] 关联 put
     */
    operator fun set(contact: Contact, value: PixivHelper) = when(contact) {
        is User -> users[contact.id] = value
        is Group -> groups[contact.id] =  value
        else -> throw IllegalAccessException("未知类型联系人!")
    }

    fun closeAll() {
        (users.values + groups.values).forEach {
            runBlocking {
                it.contact.sendMessage("机器人关闭中")
            }
            it.close()
        }
    }
}