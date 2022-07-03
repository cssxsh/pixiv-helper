package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "statistic_user")
public data class StatisticUserInfo(
    @Id
    @Column(name = "uid")
    val uid: Long,
    @Column(name = "count")
    val count: Long,
    @Column(name = "ero")
    val ero: Long
) : PixivEntity {
    public companion object SQL
}
