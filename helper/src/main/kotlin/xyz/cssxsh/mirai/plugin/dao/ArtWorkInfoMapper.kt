package xyz.cssxsh.mirai.plugin.dao

import xyz.cssxsh.mirai.plugin.model.ArtWorkInfo

interface ArtWorkInfoMapper {
    fun findByPid(pid: Long): ArtWorkInfo?
    fun countByUid(uid: Long): Long
    fun replaceArtWork(info: ArtWorkInfo)
    fun updateArtWork(info: ArtWorkInfo)
    fun artworks(interval: LongRange): List<ArtWorkInfo>
    fun findByTag(tag: String, bookmark: Long): List<ArtWorkInfo>
    fun count(): Long
    fun deleteByPid(pid: Long, comment: String)
    fun deleteByUid(uid: Long, comment: String)
    fun userArtWork(uid: Long): List<ArtWorkInfo>
    fun userEroCount(): List<Pair<Long, Long>>
    fun eroRandom(limit: Int): List<ArtWorkInfo>
    fun eroCount(): Long
    fun r18Count(): Long
    fun contains(pid: Long): Boolean
}