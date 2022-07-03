package xyz.cssxsh.mirai.pixiv.model

import xyz.cssxsh.pixiv.*
import java.time.*

public data class NaviRankAllTime(
    val title: String,
    val records: List<NaviRankRecord>
)

public data class NaviRankOverTime(
    val title: String,
    val records: Map<String, List<NaviRankRecord>>
)

public data class NaviRankRecord(
    override val pid: Long,
    override val title: String,
    val type: WorkContentType,
    val page: Int,
    val date: LocalDate,
    override val uid: Long,
    override val name: String,
    val tags: List<String>
) : SimpleArtworkInfo