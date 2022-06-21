package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

public sealed interface SearchResult {
    public val md5: String
    public val similarity: Double
}

@Serializable
@Entity
@Table(name = "statistic_search")
public data class PixivSearchResult(
    @Id
    @Column(name = "md5", nullable = false, updatable = false)
    @SerialName("md5")
    override var md5: String = "",
    @Column(name = "similarity", nullable = false)
    @SerialName("similarity")
    override var similarity: Double = 0.0,
    @Column(name = "pid", nullable = false, updatable = false)
    @SerialName("pixiv_id")
    override var pid: Long = 0,
    @Column(name = "title", nullable = false)
    @SerialName("title")
    override var title: String = "",
    @Column(name = "uid", nullable = false)
    @SerialName("member_id")
    override var uid: Long = 0,
    @Column(name = "name", nullable = false)
    @SerialName("member_name")
    override var name: String = ""
) : SimpleArtworkInfo, SearchResult, PixivEntity {
    @ManyToOne(cascade = [], fetch = FetchType.EAGER)
    @JoinColumn(name = "pid", insertable = false, updatable = false, nullable = true)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    @kotlinx.serialization.Transient
    val artwork: ArtWorkInfo? = null

    public companion object SQL
}

public data class TwitterSearchResult(
    override val md5: String = "",
    override val similarity: Double = 0.0,
    val tweet: String = "",
    val image: String = "",
) : SearchResult

public data class OtherSearchResult(
    override val md5: String = "",
    override val similarity: Double = 0.0,
    val text: String = "",
) : SearchResult

@Serializable
public data class JsonSearchResults(
    @SerialName("header")
    val info: Info,
    @SerialName("results")
    val results: List<JsonSearchResult>? = null
) {
    @Serializable
    public data class Info(
        @SerialName("account_type")
        val accountType: Int,
        @SerialName("index")
        val index: Map<Int, MapValue> = emptyMap(),
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
        public data class MapValue(
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
public data class JsonSearchResult(
    @SerialName("data")
    val `data`: JsonObject,
    @SerialName("header")
    val info: Info
) {
    @Serializable
    public data class Info(
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