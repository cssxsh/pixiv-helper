package xyz.cssxsh.pixiv.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    @SerialName("uid")
    val uid: Long,
    @SerialName("name")
    val name: String,
    @SerialName("account")
    val account: String
)
