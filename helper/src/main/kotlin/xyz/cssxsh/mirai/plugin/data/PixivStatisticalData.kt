package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.PixivHelperLogger

object PixivStatisticalData : AutoSavePluginData("PixivStatistics"), PixivHelperLogger {
    val eroCount: MutableMap<Long, Int> by value(mutableMapOf())

    val tagCount: MutableMap<Long, MutableMap<String, Int>> by value(mutableMapOf())

    fun eroAdd(id: Long): Int = eroCount.run {
        (getOrDefault(id, 0) + 1).also {
            put(id, it)
        }
    }

    fun tagAdd(id: Long, tag: String): Int = tagCount.getOrPut(id) { mutableMapOf() }.run {
        (getOrDefault(tag, 0) + 1).also {
            put(tag, it)
        }
    }
}