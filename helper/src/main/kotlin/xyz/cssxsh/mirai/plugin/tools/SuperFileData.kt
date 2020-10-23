package xyz.cssxsh.mirai.plugin.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SuperFileData(
    @SerialName("md5")
    val md5: String,
    @SerialName("partseq")
    val partSeq: String,
    @SerialName("request_id")
    val requestId: Long,
    @SerialName("uploadid")
    val uploadId: String
)