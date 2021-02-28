package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.pixiv.data.SearchResult

object PixivSearchData : AutoSavePluginData("PixivSearch") {

    @ValueName("results")
    val results: MutableMap<String, SearchResult> by value(mutableMapOf())
}