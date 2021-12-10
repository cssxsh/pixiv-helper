package xyz.cssxsh.mirai.plugin.model

import javax.persistence.*

@Entity
@Table(name = "twitter")
data class Twitter(
    @Id
    @Column(name = "screen", nullable = false, length = 50)
    val screen: String = "",
    @Column(name = "uid", nullable = false, updatable = false)
    val uid: Long,
) : PixivEntity {
    companion object SQL
}