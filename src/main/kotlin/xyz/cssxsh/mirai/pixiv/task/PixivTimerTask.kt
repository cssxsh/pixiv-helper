package xyz.cssxsh.mirai.pixiv.task

import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*

@Serializable
public sealed class PixivTimerTask {
    public abstract var cron: DataCron
    public abstract val user: Long?
    public abstract val subject: Long?

    @Serializable
    @SerialName("User")
    public data class User(
        @SerialName("uid")
        val uid: Long,
        @SerialName("cron")
        override var cron: DataCron,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long,
    ) : PixivTimerTask() {
        @Transient
        override val id: String = "User($uid)[$subject]"
    }

    @Serializable
    @SerialName("Rank")
    public data class Rank(
        @SerialName("mode")
        val mode: RankMode,
        @SerialName("cron")
        override var cron: DataCron,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long,
    ) : PixivTimerTask() {
        @Transient
        override val id: String = "Rank[$subject]"
    }

    @Serializable
    @SerialName("Follow")
    public data class Follow(
        @SerialName("cron")
        override var cron: DataCron,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long,
    ) : PixivTimerTask() {
        @Transient
        override val id: String = "Follow[$subject]"
    }

    @Serializable
    @SerialName("Recommended")
    public data class Recommended(
        @SerialName("cron")
        override var cron: DataCron,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long,
    ) : PixivTimerTask() {
        @Transient
        override val id: String = "Recommended[$subject]"
    }

    @Serializable
    @SerialName("Trending")
    public data class Trending(
        @SerialName("cron")
        override var cron: DataCron,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long
    ) : PixivTimerTask() {
        @Transient
        override val id: String = "Trending[$subject]"
    }

    @Serializable
    @SerialName("Cache")
    public data class Cache(
        @SerialName("uid")
        val uid: Long,
        @SerialName("cron")
        override var cron: DataCron,
        @SerialName("arguments")
        val arguments: String,
        @SerialName("user")
        override val user: Long?,
        @SerialName("subject")
        override val subject: Long
    ) : PixivTimerTask() {
        @Transient
        override val id: String = "Cache($arguments)[$subject]"
    }
}