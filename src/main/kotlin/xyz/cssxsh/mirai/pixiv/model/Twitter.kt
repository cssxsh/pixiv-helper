package xyz.cssxsh.mirai.pixiv.model

import javax.persistence.*

@Entity
@Table(name = "twitter")
data class Twitter(
    @Id
    @Column(name = "screen", nullable = false, length = 50)
    val screen: String = "",
    @Column(name = "uid", nullable = false, updatable = false)
    override val uid: Long,
) : PixivEntity, Author {
    companion object SQL
}