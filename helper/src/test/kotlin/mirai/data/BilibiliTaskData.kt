package mirai.data

import com.soywiz.klock.wrapped.WDateTime
import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

object BilibiliTaskData : AutoSavePluginData("BilibiliTaskData") {
    val video: MutableMap<Long, TaskInfo> by value()

    val live: MutableMap<Long, TaskInfo> by value()


    @Serializable
    data class TaskInfo(
        val last: Long = WDateTime.nowUnixLong(),
        val friends: Set<Long> = emptySet(),
        val groups: Set<Long> = emptySet()
    )
}