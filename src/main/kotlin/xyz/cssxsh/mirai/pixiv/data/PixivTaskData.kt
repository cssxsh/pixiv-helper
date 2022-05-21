package xyz.cssxsh.mirai.pixiv.data

import net.mamoe.mirai.console.data.*
import xyz.cssxsh.mirai.pixiv.*

object PixivTaskData : AutoSavePluginConfig("PixivTask"), PixivHelperConfig {
    @ValueName("tasks")
    val tasks: MutableMap<String, TimerTask> by value()
}