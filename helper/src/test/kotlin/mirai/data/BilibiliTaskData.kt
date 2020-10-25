package mirai.data

import com.soywiz.klock.wrapped.WDateTime
import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.utils.minutesToMillis

object BilibiliTaskData : AutoSavePluginData("BilibiliTaskData") {
    val video: MutableMap<Long, TaskInfo> by value()

    val live: MutableMap<Long, TaskInfo> by value()

    val minIntervalMillis: Long by value(5.minutesToMillis)

    val maxIntervalMillis: Long by value(10.minutesToMillis)

    @Serializable
    data class TaskInfo(
        val last: Long = WDateTime.nowUnixLong(),
        val friends: Set<Long> = emptySet(),
        val groups: Set<Long> = emptySet()
    )
}