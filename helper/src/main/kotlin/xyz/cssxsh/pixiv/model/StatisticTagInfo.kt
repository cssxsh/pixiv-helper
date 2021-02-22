package xyz.cssxsh.pixiv.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
