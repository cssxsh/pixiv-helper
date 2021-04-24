package xyz.cssxsh.mirai.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileInfo(
    @SerialName("pid")
    val pid: Long,
    @SerialName("index")
    val index: Int,
    @SerialName("md5")
    val md5: String,
    @SerialName("url")
    val url: String,
    @SerialName("size")
    val size: Long?
)