package xyz.cssxsh.mirai.plugin

import org.junit.jupiter.api.Test
import xyz.cssxsh.mirai.plugin.model.*
import java.io.*

internal class PixivSqlLoadKtTest {

    init {
        HelperSqlConfiguration.load(dir = File("../test/"))
    }

    @Test
    fun artwork() {
       println(ArtWorkInfo.random(0, 0, 3))
    }

    @Test
    fun statistic() {
        println(StatisticEroInfo.group(1130884806))
    }

    @Test
    fun reload() {
        reload(path = "../test/pixiv.sqlite", chunk = 8196) { result ->
            result.onSuccess { (table, count) ->
                println("${table.name}:${count}")
            }.onFailure {
                println(it.message)
            }
        }
    }
}