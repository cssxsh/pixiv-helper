package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "statistic_ero")
public data class StatisticEroInfo(
    @Id
    @Column(name = "sender", nullable = false)
    val sender: Long = 0,
    @Column(name = "`group`", nullable = true)
    val group: Long? = null,
    @Column(name = "pid", nullable = false)
    val pid: Long = 0,
    @Id
    @Column(name = "timestamp", nullable = false)
    val timestamp: Long = 0
) : PixivEntity {
    public companion object SQL
}