package xyz.cssxsh.pixiv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.cssxsh.pixiv.WorkContentType
import xyz.cssxsh.pixiv.data.app.IllustInfo
import java.time.OffsetDateTime

@Serializable
data class BaseInfo(
    @SerialName("id")
    val pid: Long,
    @SerialName("title")
    val title: String,
    @SerialName("caption")
    val caption: String,
    @SerialName("create_date")
    @Serializable(with = JapanDateTimeSerializer::class)
    val createDate: OffsetDateTime,
    @SerialName("page_count")
    val pageCount: Int,
    @SerialName("sanity_level")
    val sanityLevel: Int,
    @SerialName("type")
    val type: WorkContentType,
    @SerialName("width")
    val width: Int,
    @SerialName("height")
    val height: Int,
    @SerialName("tags")
    val tags: List<Tag>,
    @SerialName("user_id")
    val uid: Long,
    @SerialName("user_name")
    val uname: String,
    @SerialName("total_bookmarks")
    val totalBookmarks: Long,
    @SerialName("origin_url")
    val originUrl: List<String>
) {
    fun getCreateDateText(): String =
        createDate.format(JapanDateTimeSerializer.dateFormat)

    companion object {
        fun IllustInfo.toBaseInfo() = BaseInfo(
            pid = pid,
            title = title,
            caption = caption,
            createDate = createDate,
            pageCount = pageCount,
            sanityLevel = sanityLevel,
            type = type,
            width = width,
            height = height,
            tags = tags.map { Tag(it.name, it.translatedName) },
            uid = user.id,
            uname = user.name,
            totalBookmarks = totalBookmarks ?: 0,
            originUrl = getOriginUrl()
        )
    }

    @Serializable
    data class Tag(
        @SerialName("name")
        val name: String,
        @SerialName("translated_name")
        val translatedName: String?
    )
}