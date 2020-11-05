package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.PixivHelperLogger

object PixivSearchData : AutoSavePluginData("PixivSearch"), PixivHelperLogger {

    @ValueName("result_map")
    val resultMap: MutableMap<String, SearchResult> by value(mutableMapOf())
}