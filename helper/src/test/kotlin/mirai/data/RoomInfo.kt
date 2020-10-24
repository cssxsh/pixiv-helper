package mirai.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomInfo(
    @SerialName("code")
    val code: Int,
    @SerialName("data")
    val roomData: RoomData,
    @SerialName("message")
    val message: String,
    @SerialName("ttl")
    val ttl: Int
) {
    @Serializable
    data class RoomData(
        @SerialName("broadcast_type")
        val broadcastType: Int,
        @SerialName("cover")
        val cover: String,
        @SerialName("liveStatus")
        val liveStatus: Int,
        @SerialName("online")
        val online: Int,
        @SerialName("online_hidden")
        val onlineHidden: Int,
        @SerialName("roomStatus")
        val roomStatus: Int,
        @SerialName("roomid")
        val roomId: Long,
        @SerialName("roundStatus")
        val roundStatus: Int,
        @SerialName("title")
        val title: String,
        @SerialName("url")
        val url: String
    )
}