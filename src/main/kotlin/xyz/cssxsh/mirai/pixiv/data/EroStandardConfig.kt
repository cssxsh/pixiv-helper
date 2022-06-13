package xyz.cssxsh.mirai.pixiv.data

import xyz.cssxsh.pixiv.*

public interface EroStandardConfig {

    public val types: Set<WorkContentType>

    public val marks: Long

    public val pages: Int

    public val tagExclude: Regex

    public val userExclude: Set<Long>
}