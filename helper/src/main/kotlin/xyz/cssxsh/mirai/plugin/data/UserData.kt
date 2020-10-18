package xyz.cssxsh.mirai.plugin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserData(
    @SerialName("ero_count")
    val eroCount: Int = 0,
    @SerialName("tag_count")
    val tagCount: Map<String, Int> = emptyMap()
)