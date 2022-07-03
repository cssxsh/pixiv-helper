package xyz.cssxsh.mirai.pixiv.tools

import io.ktor.http.*
import org.jsoup.nodes.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.pixiv.*
import java.time.*
import java.time.format.*

public object NaviRank : HtmlParser(name = "NaviRank") {

    private const val API = "http://pixiv.navirank.com/"

    private val type: (String) -> WorkContentType = {
        when {
            it.startsWith("イラスト") -> WorkContentType.ILLUST
            it.startsWith("うごく") -> WorkContentType.UGOIRA
            it.startsWith("漫画") -> WorkContentType.MANGA
            else -> throw IllegalArgumentException("未知类型 $it")
        }
    }

    private val NUM = """\d+""".toRegex()

    private val record: (Element) -> NaviRankRecord = { element ->
        NaviRankRecord(
            // index = element.select(".num").text().toInt(),
            pid = element.select(".title").findAll(NUM).first().value.toLong(),
            title = element.select(".title").text().trim(),
            type = element.select(".type").text().let(type),
            page = element.select(".type").findAll(NUM).firstOrNull()?.value?.toInt() ?: 1,
            date = element.select(".date").text().let { LocalDate.parse(it) },
            uid = element.select(".user_name").findAll(NUM).first().value.toLong(),
            name = element.select(".user_name").text().trim(),
            tags = element.select(".tag").map { it.text().trim() }
        )
    }

    private val all: (Document) -> NaviRankAllTime = { document ->
        NaviRankAllTime(
            title = document.select("#cheader").text(),
            records = document.select(".irank").map(record)
        )
    }

    private val over: (Document) -> NaviRankOverTime = { document ->
        NaviRankOverTime(
            title = document.select("#cheader").text(),
            records = document.select("#over").associate { element ->
                element.select("h3").text() to element.select(".irank").map(record)
            }
        )
    }

    public val START: YearMonth = YearMonth.of(2008, 5)

    private fun Year.path() = format(DateTimeFormatter.ofPattern("yyyy"))

    private fun YearMonth.path() = format(DateTimeFormatter.ofPattern("yyyy/MM"))

    private suspend fun all(path: String) = html(all) {
        url {
            takeFrom(API)
            encodedPath = "/all/${path}"
        }
        method = HttpMethod.Get
    }

    public suspend fun getAllRank(): NaviRankAllTime = all(path = "")

    public suspend fun getAllRank(year: Year): NaviRankAllTime = all(path = year.path())

    public suspend fun getAllRank(month: YearMonth): NaviRankAllTime = all(path = month.path())

    private suspend fun over(path: String) = html(over) {
        url {
            takeFrom(API)
            encodedPath = "/over/${path}"
        }
        method = HttpMethod.Get
    }

    public suspend fun getOverRank(): NaviRankOverTime = over(path = "")

    public suspend fun getOverRank(year: Year): NaviRankOverTime = over(path = year.path())

    public suspend fun getOverRank(month: YearMonth): NaviRankOverTime = over(path = month.path())

    private suspend fun tag(path: String, vararg words: String) = html(all) {
        check(words.isNotEmpty()) { "关键词不能为空" }
        url {
            takeFrom(API)
            encodedPath = "/tag/${words.joinToString("%0A")}/${path}"
        }
        method = HttpMethod.Get
    }

    public suspend fun getTagRank(vararg words: String): NaviRankAllTime = tag(path = "", words = words)

    public suspend fun getTagRank(year: Year, vararg words: String): NaviRankAllTime =
        tag(path = year.path(), words = words)

    public suspend fun getTagRank(month: YearMonth, vararg words: String): NaviRankAllTime =
        tag(path = month.path(), words = words)
}