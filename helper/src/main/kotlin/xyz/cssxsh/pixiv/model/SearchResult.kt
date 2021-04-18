package xyz.cssxsh.pixiv.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    @SerialName("md5")
    val md5: String = "",
    @SerialName("similarity")
    val similarity: Double,
    @SerialName("pid")
    val pid: Long,
    @SerialName("uid")
    val uid: Long,
    @SerialName("content")
    val content: String
)