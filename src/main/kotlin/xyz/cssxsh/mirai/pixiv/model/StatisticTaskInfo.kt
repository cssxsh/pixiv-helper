package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "statistic_task")
public data class StatisticTaskInfo(
    @Id
    @Column(name = "task", nullable = false)
    val task: String = "",
    @Id
    @Column(name = "pid", nullable = false)
    val pid: Long = 0,
    @Column(name = "timestamp", nullable = false)
    val timestamp: Long = 0
) : PixivEntity {
    public companion object SQL
}