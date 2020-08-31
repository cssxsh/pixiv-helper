package xyz.cssxsh.mirai.plugin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.cssxsh.pixiv.client.PixivConfig
import xyz.cssxsh.pixiv.data.AuthResult

@Serializable
data class PixivClientData(
    @SerialName("config")
    val config: PixivConfig = PixivConfig(),
    @SerialName("auth_info")
    val authInfo: AuthResult.AuthInfo? = null
)