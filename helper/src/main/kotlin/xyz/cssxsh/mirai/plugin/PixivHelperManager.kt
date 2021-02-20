package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.mamoe.mirai.contact.*

object PixivHelperManager : LinkedHashMap<Contact, PixivHelper>() {

    override fun get(key: Contact): PixivHelper = super.get(key) ?: PixivHelper(key).also { put(key, it) }

    override fun put(key: Contact, value: PixivHelper) = super.put(key, value).also { helper ->
        if (helper != null && key is User) {
            value.launch {
                value.sign {
                    "目前测试私聊模式中使用用户自己的账户，请使用 pixiv login <uid> <password> 指令尝试登陆"
                }
            }
        }
    }

    override fun remove(key: Contact) = super.remove(key)?.apply {
        cancel()
    }

    override fun clear() = synchronized(size) {
        forEach { (_, helper) ->
            helper.cancel()
        }
        super.clear()
    }
}