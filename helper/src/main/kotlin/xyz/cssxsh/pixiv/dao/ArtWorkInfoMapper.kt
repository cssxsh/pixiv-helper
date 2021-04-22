package xyz.cssxsh.pixiv.dao

import xyz.cssxsh.pixiv.model.ArtWorkInfo

interface ArtWorkInfoMapper {
    fun findByPid(pid: Long): ArtWorkInfo?
    fun countByUid(uid: Long): Long
    fun replaceArtWork(info: ArtWorkInfo)
    fun updateArtWork(info: ArtWorkInfo)
    fun artworks(interval: LongRange): List<ArtWorkInfo>
    fun findByTag(tag: String): List<ArtWorkInfo>
    fun count(): Long
    fun deleteByPid(pid: Long, comment: String)
    fun deleteByUid(uid: Long, comment: String)
    fun userArtWork(uid: Long): List<ArtWorkInfo>
    fun userEroCount(): Map<Long, Long>
    fun eroRandom(limit: Int): List<ArtWorkInfo>
    fun eroCount(): Long
    fun r18Count(): Long
    fun contains(pid: Long): Boolean
}