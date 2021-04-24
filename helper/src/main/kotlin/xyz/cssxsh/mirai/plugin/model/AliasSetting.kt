package xyz.cssxsh.mirai.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AliasSetting(
    @SerialName("alias")
    val alias: String,
    @SerialName("uid")
    val uid: Long
)
