package xyz.cssxsh.mirai.pixiv.data

import net.mamoe.mirai.console.data.*
import xyz.cssxsh.mirai.pixiv.*

public object PixivTaskData : AutoSavePluginConfig("PixivTask"), PixivHelperConfig {
    @ValueName("tasks")
    public val tasks: MutableMap<String, TimerTask> by value()
}