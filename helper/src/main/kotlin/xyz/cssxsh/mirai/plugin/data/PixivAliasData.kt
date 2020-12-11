package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

object PixivAliasData : AutoSavePluginData("PixivAlias") {

    @ValueName("aliases")
    val aliases: MutableMap<String, Long> by value(mutableMapOf())
}