package xyz.cssxsh.pixiv.dao

import xyz.cssxsh.pixiv.model.TagInfo

interface TagInfoMapper {
    fun findByPid(pid: Long): List<TagInfo>
    fun findByName(name: String): List<Long>
    fun replaceTags(list: List<TagInfo>): Boolean
}