package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.contact.User
import xyz.cssxsh.mirai.plugin.PixivHelperLogger

object PixivStatisticalData : AutoSavePluginData("PixivStatistics"), PixivHelperLogger {
    private val eroCount: MutableMap<Long, Int> by value(mutableMapOf())

    private val tagCount: MutableMap<Long, MutableMap<String, Int>> by value(mutableMapOf())

    fun eroAdd(user: User): Int = eroCount.run {
        (getOrDefault(user.id, 0) + 1).also {
            put(user.id, it)
        }
    }

    fun tagAdd(user: User, tag: String): Int = tagCount.getOrPut(user.id) { mutableMapOf() }.run {
        (getOrDefault(tag, 0) + 1).also {
            put(tag, it)
        }
    }

    fun getCount(user: User) = eroCount.getOrPut(user.id, { 0 }) to tagCount.getOrPut(user.id) { mutableMapOf() }.toMap()
}