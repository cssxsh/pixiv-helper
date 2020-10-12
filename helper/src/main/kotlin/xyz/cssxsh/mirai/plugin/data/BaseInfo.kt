package xyz.cssxsh.mirai.plugin.data

import com.soywiz.klock.wrapped.WDateTimeTz
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.cssxsh.pixiv.WorkContentType
import xyz.cssxsh.pixiv.data.app.IllustInfo

@Serializable
data class BaseInfo(
    @SerialName("id")
    val pid: Long,
    @SerialName("title")
    val title: String,
    @SerialName("create_date")
    @Serializable(with = IllustInfo.Companion.CreateDateSerializer::class)
    val createDate: WDateTimeTz,
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
    val totalBookmarks: Long
) {
    companion object {
        fun IllustInfo.toBaseInfo() = BaseInfo(
            pid = pid,
            title = title,
            createDate = createDate,
            pageCount = pageCount,
            sanityLevel = sanityLevel,
            type = type,
            width = width,
            height = height,
            tags = tags.map { Tag(it.name, it.translatedName) },
            uid = user.id,
            uname = user.name,
            totalBookmarks = totalBookmarks ?: 0
        )
    }

    fun getOriginUrl(): List<String> = (0 until pageCount).map { index ->
        "https://i.pximg.net/img-original/img/${createDate.format("yyyy/MM/dd/HH/mm/ss")}/${pid}_p${index}.jpg"
    }

    @Serializable
    data class Tag(
        @SerialName("name")
        val name: String,
        @SerialName("translated_name")
        val translatedName: String?
    )
}