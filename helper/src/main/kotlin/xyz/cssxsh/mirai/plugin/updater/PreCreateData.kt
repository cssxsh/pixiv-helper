package xyz.cssxsh.mirai.plugin.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PreCreateData(
    @SerialName("block_list")
    val blockList: List<Int> = emptyList(),
    @SerialName("path")
    val path: String? = null,
    @SerialName("errno")
    val errno: Int,
    @SerialName("request_id")
    val requestId: Long,
    @SerialName("return_type")
    val returnType: Int,
    @SerialName("uploadid")
    val uploadId: String
)