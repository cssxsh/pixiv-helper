package xyz.cssxsh.pixiv.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtWorkInfo(
    @SerialName("pid")
    val pid: Long,
    @SerialName("uid")
    val uid: Long,
    @SerialName("title")
    val title: String,
    @SerialName("caption")
    val caption: String,
    @SerialName("create_at")
    val createAt: Long,
    @SerialName("page_count")
    val pageCount: Int,
    @SerialName("sanity_level")
    val sanityLevel: Int,
    @SerialName("type")
    val type: Int,
    @SerialName("width")
    val width: Int,
    @SerialName("height")
    val height: Int,
    @SerialName("total_bookmarks")
    val totalBookmarks: Long,
    @SerialName("total_comments")
    val totalComments: Long,
    @SerialName("total_view")
    val totalView: Long,
    @SerialName("age")
    val age: Int,
    @SerialName("is_ero")
    val isEro: Boolean,
    @SerialName("deleted")
    val deleted: Boolean
)
