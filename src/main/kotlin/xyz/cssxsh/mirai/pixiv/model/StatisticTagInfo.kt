package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "statistic_tag")
public data class StatisticTagInfo(
    @Id
    @Column(name = "sender", nullable = false)
    val sender: Long,
    @Column(name = "`group`", nullable = true)
    val group: Long?,
    @Column(name = "pid", nullable = true)
    val pid: Long?,
    @Column(name = "tag", nullable = false)
    val tag: String,
    @Id
    @Column(name = "timestamp", nullable = false)
    val timestamp: Long
) : PixivEntity {
    public companion object SQL
}