package xyz.cssxsh.mirai.pixiv.data

import net.mamoe.mirai.console.data.*
import xyz.cssxsh.mirai.pixiv.task.*

public object PixivTaskData : AutoSavePluginData("PixivTask") {
    @ValueName("tasks")
    public val tasks: MutableMap<String, PixivTimerTask> by value()
}