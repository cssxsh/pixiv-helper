package xyz.cssxsh.mirai.plugin.dao

import org.apache.ibatis.annotations.Param
import xyz.cssxsh.mirai.plugin.model.ArtWorkInfo

interface ArtWorkInfoMapper {
    fun findByPid(pid: Long): ArtWorkInfo?
    fun countByUid(uid: Long): Long
    fun replaceArtWork(info: ArtWorkInfo)
    fun updateArtWork(info: ArtWorkInfo)
    fun artworks(interval: LongRange): List<ArtWorkInfo>
    fun findByTag(
        @Param("tag") tag: String,
        @Param("bookmarks") bookmarks: Long,
        @Param("fuzzy") fuzzy: Boolean
    ): List<ArtWorkInfo>

    fun count(): Long
    fun deleteByPid(@Param("pid") pid: Long, @Param("comment") comment: String)
    fun deleteByUid(@Param("pid") uid: Long, @Param("comment") comment: String)
    fun userArtWork(uid: Long): List<ArtWorkInfo>
    fun userEroCount(): List<Pair<Long, Long>>
    fun eroRandom(
        @Param("limit") limit: Int,
        @Param("level") level: Int,
        @Param("bookmarks") bookmarks: Long
    ): List<ArtWorkInfo>

    fun eroCount(): Long
    fun r18Count(): Long
    fun contains(pid: Long): Boolean
    fun noCache(): Set<Long>
}