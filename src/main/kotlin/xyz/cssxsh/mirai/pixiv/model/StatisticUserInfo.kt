package xyz.cssxsh.mirai.pixiv.model

import javax.persistence.*

@Entity
@Table(name = "statistic_user")
data class StatisticUserInfo(
    @Id
    @Column(name = "uid")
    val uid: Long,
    @Column(name = "count")
    val count: Long,
    @Column(name = "ero")
    val ero: Long
) : PixivEntity {
    companion object SQL
}
