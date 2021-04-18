package xyz.cssxsh.pixiv.dao

interface DeleteInfoMapper {
    fun add(pid: Long, comment: String, timestamp: Long): Boolean
    fun contains(pid: Long): Boolean
}