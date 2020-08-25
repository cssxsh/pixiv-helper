@file:Suppress("unused")
package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.contact.Group
import xyz.cssxsh.mirai.plugin.setting.PixivClientSetting
import xyz.cssxsh.pixiv.client.SimplePixivClient

class PixivHelper(val contact: Contact) : SimplePixivClient(proxyUrl = PixivHelperSettings.proxy) {

    val setting: PixivClientSetting
        get() = when(contact) {
            is User -> PixivHelperSettings.users.getOrPut(contact.id) { PixivClientSetting() }
            is Group -> PixivHelperSettings.groups.getOrPut(contact.id) { PixivClientSetting() }
            else -> throw Exception("未知类型联系人!")
        }

    override suspend fun refresh(refreshToken: String) {
        super.refresh(refreshToken)
        setting.refreshToken = refreshToken
        contact.sendMessage("Auth by RefreshToken: $refreshToken")
    }

    suspend fun refresh() {
        refresh(setting.refreshToken)
    }

    override suspend fun login(mailOrPixivID: String, password: String) {
        super.login(mailOrPixivID, password)
        setting.mailOrPixivID = mailOrPixivID
        setting.password = password
        contact.sendMessage("Auth by Account: $mailOrPixivID")
    }

    suspend fun login() {
        login(setting.mailOrPixivID, setting.password)
    }
}