package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "users")
public data class UserBaseInfo(
    @Id
    @Column(name = "uid", nullable = false, updatable = false)
    override val uid: Long,
    @Column(name = "name", nullable = false, length = 15)
    val name: String,
    @Column(name = "account", nullable = true, length = 32)
    val account: String?
) : PixivEntity, Author {
    public companion object SQL
}