package mirai.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

object BilibiliTaskData : AutoSavePluginData("BilibiliTaskData") {
    val video: MutableMap<Long, Long> by value()

    val live: MutableMap<Long, Boolean> by value()
}