package xyz.cssxsh.mirai.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

sealed interface SearchResult {
    val similarity: Double
}

@Serializable
data class PixivSearchResult(
    @SerialName("md5")
    val md5: String = "",
    @SerialName("similarity")
    override val similarity: Double = 0.0,
    @SerialName("pixiv_id")
    override val pid: Long,
    @SerialName("title")
    override val title: String,
    @SerialName("member_id")
    override val uid: Long,
    @SerialName("member_name")
    override val name: String
): SimpleArtworkInfo, SearchResult

data class TwitterSearchResult(
    override val similarity: Double,
    val tweet: String,
    val image: String,
): SearchResult

data class OtherSearchResult(
    override val similarity: Double,
    val text: String,
): SearchResult

@Serializable
data class JsonSearchResults(
    @SerialName("header")
    val info: Info,
    @SerialName("results")
    val results: List<JsonSearchResult> = emptyList()
) {
    @Serializable
    data class Info(
        @SerialName("account_type")
        val accountType: Int,
        @SerialName("index")
        val index: Map<Int,MapValue> = emptyMap(),
        @SerialName("long_limit")
        val longLimit: Int,
        @SerialName("long_remaining")
        val longRemaining: Int,
        @SerialName("minimum_similarity")
        val minimumSimilarity: Double = 0.0,
        @SerialName("query_image")
        val queryImage: String? = null,
        @SerialName("query_image_display")
        val queryImageDisplay: String? = null,
        @SerialName("results_requested")
        val resultsRequested: Int,
        @SerialName("results_returned")
        val resultsReturned: Int? = null,
        @SerialName("search_depth")
        val searchDepth: Int = 0,
        @SerialName("short_limit")
        val shortLimit: Int,
        @SerialName("short_remaining")
        val shortRemaining: Int,
        @SerialName("status")
        val status: Int,
        @SerialName("user_id")
        val userId: String
    ) {
        @Serializable
        data class MapValue(
            @SerialName("id")
            val id: Int,
            @SerialName("parent_id")
            val parentId: Int,
            @SerialName("results")
            val results: Int = 0,
            @SerialName("status")
            val status: Int
        )
    }
}

@Serializable
data class JsonSearchResult(
    @SerialName("data")
    val `data`: JsonObject,
    @SerialName("header")
    val info: Info
) {
    @Serializable
    data class Info(
        @SerialName("dupes")
        val dupes: Int,
        @SerialName("index_id")
        val indexId: Int,
        @SerialName("index_name")
        val indexName: String,
        @SerialName("similarity")
        val similarity: Double,
        @SerialName("thumbnail")
        val thumbnail: String
    )
}