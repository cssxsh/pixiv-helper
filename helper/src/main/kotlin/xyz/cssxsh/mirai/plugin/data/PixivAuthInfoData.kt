package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.pixiv.client.PixivConfig
import xyz.cssxsh.pixiv.data.AuthResult

object PixivAuthInfoData : AutoSavePluginData() {
    /**
     * 用于存储群关联
     */
    val authData: MutableMap<Long, AuthResult.AuthInfo> by value(mutableMapOf())

    fun findByConfig(config: PixivConfig) = authData.toList().find { (uid, info) ->
        config.run {
            (account?.mailOrUID?: "").let {
                it == uid.toString() || it == info.user.mailAddress
            } || refreshToken == info.refreshToken
        }
    }?.second
}