package xyz.cssxsh.mirai.plugin.data

import xyz.cssxsh.pixiv.WorkContentType

interface EroStandardConfig {

    val types: Set<WorkContentType>

    val bookmarks: Long

    val pages: Int

    val tagExclude: Regex

    val userExclude: Set<Long>
}