package xyz.cssxsh.mirai.plugin.model

import kotlinx.serialization.*

@Serializable
data class FanBoxCreator(
    @SerialName("coverImageUrl")
    val coverImageUrl: String,
    @SerialName("creatorId")
    val id: String,
    @SerialName("description")
    val description: String,
    @SerialName("hasAdultContent")
    val hasAdultContent: Boolean,
    @SerialName("hasBoothShop")
    val hasBoothShop: Boolean,
    @SerialName("isAcceptingRequest")
    val isAcceptingRequest: Boolean,
    @SerialName("isFollowed")
    val isFollowed: Boolean,
    @SerialName("isStopped")
    val isStopped: Boolean,
    @SerialName("isSupported")
    val isSupported: Boolean,
    @SerialName("profileItems")
    val profileItems: List<ProfileItem>,
    @SerialName("profileLinks")
    val profileLinks: List<String>,
    @SerialName("user")
    val user: User
) {
    @Serializable
    data class ProfileItem(
        @SerialName("id")
        val id: String,
        @SerialName("imageUrl")
        val imageUrl: String? = null,
        @SerialName("thumbnailUrl")
        val thumbnailUrl: String? = null,
        @SerialName("type")
        val type: String
    )

    @Serializable
    data class User(
        @SerialName("iconUrl")
        val iconUrl: String,
        @SerialName("name")
        val name: String,
        @SerialName("userId")
        override val uid: Long
    ) : Author
}