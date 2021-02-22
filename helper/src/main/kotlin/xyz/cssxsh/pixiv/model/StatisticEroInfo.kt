package xyz.cssxsh.pixiv.model

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
