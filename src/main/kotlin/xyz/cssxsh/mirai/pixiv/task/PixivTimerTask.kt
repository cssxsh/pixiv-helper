package xyz.cssxsh.mirai.pixiv.task

import kotlinx.serialization.*
import xyz.cssxsh.pixiv.*

@Serializable
public sealed class PixivTimerTask {
    public abstract val cron: DataCron
    public abstract val user: Long?
    public abstract val subject: Long?

    @Serializable
    @SerialName("User")
    public data class User(
        @SerialName("cron")
        override val cron: DataCron,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long,
    ) : PixivTimerTask()

    @Serializable
    @SerialName("Rank")
    public data class Rank(
        @SerialName("mode")
        val mode: RankMode,
        @SerialName("cron")
        override val cron: DataCron,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long,
    ) : PixivTimerTask()

    @Serializable
    @SerialName("Follow")
    public data class Follow(
        @SerialName("cron")
        override val cron: DataCron,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long,
    ) : PixivTimerTask()

    @Serializable
    @SerialName("Recommended")
    public data class Recommended(
        @SerialName("cron")
        override val cron: DataCron,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long,
    ) : PixivTimerTask()

    @Serializable
    @SerialName("Trending")
    public data class Trending(
        @SerialName("cron")
        override val cron: DataCron,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long
    ) : PixivTimerTask()

    @Serializable
    @SerialName("Cache")
    public data class Cache(
        @SerialName("uid")
        val uid: Long,
        @SerialName("cron")
        override val cron: DataCron,
        @SerialName("arguments")
        val arguments: String,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long?
    ) : PixivTimerTask()
}