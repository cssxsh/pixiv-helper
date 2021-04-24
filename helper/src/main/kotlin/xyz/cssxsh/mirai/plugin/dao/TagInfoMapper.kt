package xyz.cssxsh.mirai.plugin.dao

import xyz.cssxsh.mirai.plugin.model.TagBaseInfo

interface TagInfoMapper {
    fun findByPid(pid: Long): List<TagBaseInfo>
    fun findByName(name: String): Set<Long>
    fun replaceTags(list: List<TagBaseInfo>): Boolean
}