package xyz.cssxsh.mirai.plugin.model

import javax.persistence.*

@Entity
@Table(name = "users")
data class UserBaseInfo(
    @Id
    @Column(name = "uid", nullable = false, updatable = false)
    val uid: Long = 0,
    @Column(name = "name", nullable = false, length = 15)
    val name: String = "",
    @Column(name = "account", nullable = false, length = 32)
    val account: String = ""
) : PixivEntity {
    companion object SQL
}