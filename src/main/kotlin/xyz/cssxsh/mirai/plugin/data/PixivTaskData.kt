package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*
import xyz.cssxsh.mirai.plugin.*

object PixivTaskData : AutoSavePluginConfig("PixivTask"), PixivHelperConfig {
    @ValueName("tasks")
    val tasks: MutableMap<String, TimerTask> by value()
}