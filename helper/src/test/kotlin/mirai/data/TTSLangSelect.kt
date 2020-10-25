package mirai.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TTSLangSelect(
    @SerialName("error")
    val error: Int,
    @SerialName("msg")
    val message: String,
    @SerialName("lan")
    val language: String
)
