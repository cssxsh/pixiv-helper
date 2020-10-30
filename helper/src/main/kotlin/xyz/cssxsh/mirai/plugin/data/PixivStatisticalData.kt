package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.contact.User
import xyz.cssxsh.mirai.plugin.PixivHelperLogger

object PixivStatisticalData : AutoSavePluginData("PixivStatistics"), PixivHelperLogger {

    @ValueName("data")
    private val data: MutableMap<Long, UserData> by value(mutableMapOf())

    fun getMap() = data.toMap()

    fun eroAdd(user: User): Int = data.compute(user.id) { _, userData ->
        (userData ?: UserData()).run {
            copy(eroCount = eroCount + 1)
        }
    }!!.eroCount

    fun tagAdd(user: User, tag: String): Int = data.compute(user.id) { _, userData ->
        (userData ?: UserData()).run {
            copy(tagCount = tagCount.toMutableMap().apply {
                compute(tag) { _, old ->
                    (old ?: 0) + 1
                }
            }.toMap())
        }
    }!!.tagCount[tag]!!

    fun getCount(user: User) = data.getOrPut(user.id) { UserData() }
}