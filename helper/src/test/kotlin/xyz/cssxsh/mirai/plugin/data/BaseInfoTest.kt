package xyz.cssxsh.mirai.plugin.data

import com.soywiz.klock.PatternDateFormat
import com.soywiz.klock.parse
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import xyz.cssxsh.pixiv.WorkContentType

internal class BaseInfoTest {

    @Test
    fun getOriginUrl() {
        BaseInfo(
            pid = 84718054,
            title = "",
            createDate = PatternDateFormat(format = "yyyy-MM-dd'T'HH:mm:ssXXX").parse("2020-10-01T00:15:23+09:00"),
            pageCount = 1,
            sanityLevel = 0,
            type = WorkContentType.ILLUST,
            width = 0,
            height = 0,
            tags = emptyList(),
            uid = 0,
            uname = "",
            totalBookmarks = 0
        ).run {
            assertEquals("https://i.pximg.net/img-original/img/2020/10/01/00/15/23/84718054_p0.jpg", getOriginUrl().first())
        }
    }
}