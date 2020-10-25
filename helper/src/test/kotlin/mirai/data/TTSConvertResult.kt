package mirai.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TTSConvertResult(
    @SerialName("ext")
    val ext: String,
    @SerialName("filename")
    val filename: String,
    @SerialName("server")
    val server: String,
    @SerialName("state")
    val state: String
)