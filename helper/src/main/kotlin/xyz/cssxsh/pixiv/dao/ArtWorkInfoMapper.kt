package xyz.cssxsh.pixiv.dao

import xyz.cssxsh.pixiv.model.ArtWorkInfo

interface ArtWorkInfoMapper {
    fun findByPid(pid: Long): ArtWorkInfo?
    fun countByUid(uid: Long): Long
    fun replaceArtWork(info: ArtWorkInfo)
    fun updateArtWork(info: ArtWorkInfo)
    fun artWorks(interval: LongRange): List<ArtWorkInfo>
    fun count(): Long
    fun deleteByPid(pid: Long)
    fun userArtWork(uid: Long): List<ArtWorkInfo>
    fun userEroCount(): Map<Long, Long>
    fun eroRandom(limit: Int): List<ArtWorkInfo>
    fun eroCount(): Long
    fun r18Count(): Long
    fun contains(pid: Long): Boolean
}