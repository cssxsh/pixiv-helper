package xyz.cssxsh.mirai.plugin.setting

import kotlinx.serialization.Serializable

@Serializable
data class PixivClientSetting(
    var mailOrPixivID: String = "",
    var password: String = "",
    var refreshToken: String = "",
    var language: String = ""
)