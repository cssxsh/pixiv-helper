package xyz.cssxsh.mirai.pixiv.data

import kotlinx.serialization.*
import xyz.cssxsh.pixiv.*

@Serializable
data class EroStandardData(
    @SerialName("ero_work_types")
    override val types: Set<WorkContentType>,
    @SerialName("ero_bookmarks")
    override val marks: Long,
    @SerialName("ero_page_count")
    override val pages: Int,
    @SerialName("ero_tag_exclude")
    @Serializable(RegexSerializer::class)
    override val tagExclude: Regex,
    @SerialName("ero_user_exclude")
    override val userExclude: Set<Long>
) : EroStandardConfig
