package xyz.cssxsh.mirai.plugin

import org.hibernate.*
import org.junit.jupiter.api.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*
import java.io.*

internal class PixivSqlLoadKtTest {

    init {
        System.setProperty("xyz.cssxsh.mirai.plugin.logger", "${false}")
        HelperSqlConfiguration.load(dir = File("../test/"))
    }

    @Test
    fun artwork() {
       println(ArtWorkInfo.random(0, 0, AgeLimit.ALL, 10))
    }

    @Test
    fun tag() {
        println(ArtWorkInfo.tag("红", "明日方舟", marks = 0, fuzzy = false, age = AgeLimit.ALL, limit = 10))
    }

    @Test
    fun statistic() {
        println(StatisticEroInfo.group(1130884806))
    }

    @Test
    fun reload() {
        reload(path = "../test/pixiv.sqlite", chunk = 1 shl 16, mode = ReplicationMode.OVERWRITE) { result ->
            result.onSuccess { (table, count) ->
                println("${table.name}:${count}")
            }.onFailure {
                println(it.message)
            }
        }
    }
}