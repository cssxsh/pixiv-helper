package xyz.cssxsh.mirai.plugin.dao

import xyz.cssxsh.mirai.plugin.model.FileInfo

interface FileInfoMapper {
    fun replaceFiles(list: List<FileInfo>): Boolean
    fun fileInfos(pid: Long): List<FileInfo>
    fun findByMd5(md5: String): FileInfo?
    fun files(interval: LongRange): List<FileInfo>
}