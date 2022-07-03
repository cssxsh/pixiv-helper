package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "twitter")
public data class Twitter(
    @Id
    @Column(name = "screen", nullable = false, length = 50)
    val screen: String,
    @Column(name = "uid", nullable = false, updatable = false)
    override val uid: Long,
) : PixivEntity, Author {
    public companion object SQL
}