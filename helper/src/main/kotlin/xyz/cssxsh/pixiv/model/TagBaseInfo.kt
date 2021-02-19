package xyz.cssxsh.pixiv.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TagBaseInfo(
    @SerialName("pid")
    val pid: Long,
    @SerialName("name")
    val name: String,
    @SerialName("translated_name")
    val translatedName: String?
)
