package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.PixivHelperLogger

object PixivAliasData : AutoSavePluginData("PixivAlias"), PixivHelperLogger {

    val aliases: MutableMap<String, Long> by value(mutableMapOf())
}