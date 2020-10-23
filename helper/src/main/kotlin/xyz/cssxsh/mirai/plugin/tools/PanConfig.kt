package xyz.cssxsh.mirai.plugin.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PanConfig(
    @SerialName("bdstoken")
    val bdsToken: String,
    @SerialName("logid")
    val logId: String,
    @SerialName("target_path")
    val targetPath: String,
    @SerialName("cookies")
    val cookies: String
)