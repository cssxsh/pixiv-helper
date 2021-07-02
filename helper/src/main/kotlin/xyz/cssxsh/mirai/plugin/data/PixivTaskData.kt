package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.TimerTask

object PixivTaskData : AutoSavePluginConfig("PixivTask") {

    @ValueName("tasks")
    val tasks: MutableMap<String, TimerTask> by value(mutableMapOf())
}