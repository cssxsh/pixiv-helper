package xyz.cssxsh.pixiv.dao

import xyz.cssxsh.pixiv.model.TagBaseInfo

interface TagInfoMapper {
    fun findByPid(pid: Long): List<TagBaseInfo>
    fun findByName(name: String): List<Long>
    fun replaceTags(list: List<TagBaseInfo>): Boolean
}