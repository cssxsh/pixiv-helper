package xyz.cssxsh.mirai.plugin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PixivHelperData(
    @SerialName("users")
    val users: MutableMap<Long, PixivClientData> = mutableMapOf(),
    @SerialName("groups")
    val groups: MutableMap<Long, PixivClientData> = mutableMapOf()
)