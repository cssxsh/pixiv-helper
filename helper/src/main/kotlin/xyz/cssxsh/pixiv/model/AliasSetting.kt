package xyz.cssxsh.pixiv.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AliasSetting(
    @SerialName("alias")
    val alias: String,
    @SerialName("uid")
    val uid: Long
)
