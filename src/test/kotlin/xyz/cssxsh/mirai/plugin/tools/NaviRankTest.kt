package xyz.cssxsh.mirai.plugin.tools

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Year
import java.time.YearMonth

internal class NaviRankTest {

    @Test
    fun getAllRank() = runBlocking {
        NaviRank.getAllRank().let {
            println(it)
            assertTrue(it.records.isNotEmpty())
        }
        NaviRank.getAllRank(year = Year.now()).let {
            println(it)
            assertTrue(it.records.isNotEmpty())
        }
        NaviRank.getAllRank(month = YearMonth.now()).let {
            println(it)
            assertTrue(it.records.isNotEmpty())
        }
    }

    @Test
    fun getOverRank() = runBlocking {
        NaviRank.getOverRank().let {
            println(it)
            assertTrue(it.records.isNotEmpty())
        }
        NaviRank.getOverRank(year = Year.now()).let {
            println(it)
            assertTrue(it.records.isNotEmpty())
        }
        NaviRank.getOverRank(month = YearMonth.now()).let {
            println(it)
            assertTrue(it.records.isNotEmpty())
        }
    }

    @Test
    fun getTagRank() = runBlocking {
        NaviRank.getTagRank("巨乳", "魅惑の谷間").let {
            println(it)
            assertTrue(it.records.isNotEmpty())
        }
        NaviRank.getTagRank(year = Year.now(), "巨乳", "魅惑の谷間").let {
            println(it)
            assertTrue(it.records.isNotEmpty())
        }
        NaviRank.getTagRank(month = YearMonth.now(), "巨乳", "魅惑の谷間").let {
            println(it)
            assertTrue(it.records.isNotEmpty())
        }
    }
}