package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "statistic_ero")
public data class StatisticEroInfo(
    @Id
    @Column(name = "sender", nullable = false)
    val sender: Long,
    @Column(name = "`group`", nullable = true)
    val group: Long?,
    @Column(name = "pid", nullable = false)
    val pid: Long,
    @Id
    @Column(name = "timestamp", nullable = false)
    val timestamp: Long
) : PixivEntity {
    public companion object SQL
}