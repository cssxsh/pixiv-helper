package xyz.cssxsh.mirai.pixiv.model

import javax.persistence.*

@Entity
@Table(name = "statistic_tag")
data class StatisticTagInfo(
    @Id
    @Column(name = "sender", nullable = false)
    val sender: Long = 0,
    @Column(name = "`group`", nullable = true)
    val group: Long? = null,
    @Column(name = "pid", nullable = true)
    val pid: Long? = null,
    @Column(name = "tag", nullable = false)
    val tag: String = "",
    @Id
    @Column(name = "timestamp", nullable = false)
    val timestamp: Long = 0
) : PixivEntity {
    companion object SQL
}