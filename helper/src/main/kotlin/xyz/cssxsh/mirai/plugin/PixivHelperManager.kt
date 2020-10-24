package xyz.cssxsh.mirai.plugin

import com.soywiz.klock.wrapped.WDateTimeTz
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import xyz.cssxsh.pixiv.data.AuthResult

object PixivHelperManager : PixivHelperLogger {
    /**
     * 用于存储用户关联的助手
     */
    private val users: MutableMap<Long, PixivHelper> = mutableMapOf()

    /**
     * 用于存储群关联的助手
     */
    private val groups: MutableMap<Long, PixivHelper> = mutableMapOf()

    val userAuthInfos: MutableMap<Long, AuthResult.AuthInfo?> = mutableMapOf()

    val userExpiresTimes: MutableMap<Long, WDateTimeTz> = mutableMapOf()

    var defaultAuthInfos: AuthResult.AuthInfo? = null

    var defaultExpiresTimes: WDateTimeTz = WDateTimeTz.nowLocal()

    /**
     * 通过联系人获取
     * @param contact 助手的联系人
     * @param defaultValue 构建默认值
     */
    fun getOrPut(contact: Contact, defaultValue: () -> PixivHelper): PixivHelper = when (contact) {
        is User -> synchronized(users) {
            users.getOrPut(contact.id) {
                defaultValue().apply {
                    launch {
                        reply("目前测试私聊模式中使用用户自己的账户，请使用 pixiv login <uid> <password> 指令尝试登陆")
                    }
                }
            }
        }
        is Group -> synchronized(groups) {
            groups.getOrPut(contact.id) {
                defaultValue()
            }
        }
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
    operator fun set(contact: Contact, value: PixivHelper) = when (contact) {
        is User -> synchronized(users) { users[contact.id] = value }
        is Group -> synchronized(groups) { groups[contact.id] = value }
        else -> throw IllegalAccessException("未知类型联系人!")
    }

    fun remove(contact: Contact) = when (contact) {
        is User -> synchronized(users) { users.remove(contact.id) }
        is Group -> synchronized(groups) { groups.remove(contact.id) }
        else -> throw IllegalAccessException("未知类型联系人!")
    }?.apply {
        cancel()
    }
}