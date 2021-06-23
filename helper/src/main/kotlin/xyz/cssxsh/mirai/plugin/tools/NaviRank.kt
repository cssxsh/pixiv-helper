package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.request.*
import io.ktor.http.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.WorkContentType
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter

object NaviRank : HtmlParser(name = "NaviRank") {

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

    val START: YearMonth = YearMonth.of(2008, 5)

    private fun Year.path() = format(DateTimeFormatter.ofPattern("yyyy"))

    private fun YearMonth.path() = format(DateTimeFormatter.ofPattern("yyyy/MM"))

    private suspend fun getAllRank(path: String) = html(all) {
        url(Url(API).copy(encodedPath = "/all/${path}"))
        method = HttpMethod.Get
    }

    suspend fun getAllRank() = getAllRank(path = "")

    suspend fun getAllRank(year: Year) = getAllRank(path = year.path())

    suspend fun getAllRank(month: YearMonth) = getAllRank(path = month.path())

    private suspend fun getOverRank(path: String) = html(over) {
        url(Url(API).copy(encodedPath = "/over/${path}"))
        method = HttpMethod.Get
    }

    suspend fun getOverRank() = getOverRank(path = "")

    suspend fun getOverRank(year: Year) = getOverRank(path = year.path())

    suspend fun getOverRank(month: YearMonth) = getOverRank(path = month.path())

    private suspend fun getTagRank(path: String, vararg words: String) = html(all) {
        check(words.isNotEmpty()) { "关键词不能为空" }
        url(Url(API).copy(encodedPath = "/tag/${words.joinToString("%0A")}/${path}"))
        method = HttpMethod.Get
    }

    suspend fun getTagRank(vararg words: String) = getTagRank(path = "", words = words)

    suspend fun getTagRank(year: Year, vararg words: String) = getTagRank(path = year.path(), words = words)

    suspend fun getTagRank(month: YearMonth, vararg words: String) = getTagRank(path = month.path(), words = words)
}