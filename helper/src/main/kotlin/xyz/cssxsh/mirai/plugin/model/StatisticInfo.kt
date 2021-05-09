package xyz.cssxsh.mirai.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatisticEroInfo(
    @SerialName("sender")
    val sender: Long,
    @SerialName("group")
    val group: Long?,
    @SerialName("pid")
    val pid: Long,
    @SerialName("timestamp")
    val timestamp: Long
)

@Serializable
data class StatisticTagInfo(
    @SerialName("sender")
    val sender: Long,
    @SerialName("group")
    val group: Long?,
    @SerialName("pid")
    val pid: Long?,
    @SerialName("tag")
    val tag: String,
    @SerialName("timestamp")
    val timestamp: Long
)

@Serializable
data class StatisticTaskInfo(
    @SerialName("sender")
    val task: String,
    @SerialName("pid")
    val pid: Long,
    @SerialName("timestamp")
    val timestamp: Long
)
