package xyz.cssxsh.mirai.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    @SerialName("md5")
    val md5: String = "",
    @SerialName("similarity")
    val similarity: Double,
    @SerialName("pid")
    override val pid: Long,
    @SerialName("title")
    override val title: String,
    @SerialName("uid")
    override val uid: Long,
    @SerialName("content")
    override val name: String
): SimpleArtworkInfo