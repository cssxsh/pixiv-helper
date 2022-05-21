package xyz.cssxsh.mirai.pixiv.model

import javax.persistence.*

@Entity
@Table(name = "users")
data class UserBaseInfo(
    @Id
    @Column(name = "uid", nullable = false, updatable = false)
    override val uid: Long = 0,
    @Column(name = "name", nullable = false, length = 15)
    val name: String = "",
    @Column(name = "account", nullable = true, length = 32)
    val account: String? = null
) : PixivEntity, Author {
    companion object SQL
}