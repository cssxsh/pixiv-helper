package xyz.cssxsh.mirai.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PixivArticle(
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String,
    @SerialName("illusts")
    val illusts: List<Illust>,
) {
    @Serializable
    data class Illust(
        @SerialName("pid")
        override val pid: Long,
        @SerialName("title")
        override val title: String,
        @SerialName("uid")
        override val uid: Long,
        @SerialName("name")
        override val name: String,
    ): SimpleArtworkInfo
}