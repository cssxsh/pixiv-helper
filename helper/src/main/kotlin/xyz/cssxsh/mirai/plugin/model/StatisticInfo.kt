package xyz.cssxsh.mirai.plugin.model

import javax.persistence.*

@Entity
@Table(name = "statistic_ero")
data class StatisticEroInfo(
    @Id
    @Column(name = "sender", nullable = false)
    val sender: Long = 0,
    @Column(name = "group", nullable = true)
    val group: Long? = null,
    @Column(name = "pid", nullable = false)
    val pid: Long = 0,
    @Id
    @Column(name = "timestamp", nullable = false)
    val timestamp: Long = 0
)

@Entity
@Table(name = "statistic_tag")
data class StatisticTagInfo(
    @Id
    @Column(name = "sender", nullable = false)
    val sender: Long = 0,
    @Column(name = "group", nullable = true)
    val group: Long? = null,
    @Column(name = "pid", nullable = false)
    val pid: Long? = null,
    @Column(name = "tag", nullable = false)
    val tag: String = "",
    @Id
    @Column(name = "timestamp", nullable = false)
    val timestamp: Long = 0
)

@Entity
@Table(name = "statistic_task")
data class StatisticTaskInfo(
    @Id
    @Column(name = "task", nullable = false)
    val task: String = "",
    @Id
    @Column(name = "pid", nullable = false)
    val pid: Long = 0,
    @Column(name = "timestamp", nullable = false)
    val timestamp: Long = 0
)

@Entity
@Table(name = "statistic_alias")
data class AliasSetting(
    @Id
    @Column(name = "alias", nullable = false)
    val alias: String = "",
    @Id
    @Column(name = "uid", nullable = false)
    val uid: Long = 0
) {
    companion object
}
