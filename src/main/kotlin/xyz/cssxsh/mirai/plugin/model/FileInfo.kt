package xyz.cssxsh.mirai.plugin.model

import javax.persistence.*

@Entity
@Table(name = "files")
data class FileInfo(
    @Id
    @Column(name = "pid", nullable = false, updatable = false)
    val pid: Long = 0,
    @Id
    @Column(name = "`index`", nullable = false, updatable = false)
    val index: Int = 0,
    @Column(name = "md5", nullable = false, length = 32)
    val md5: String = "",
    @Column(name = "url", nullable = false)
    val url: String = "",
    @Column(name = "size", nullable = false)
    val size: Int = 0
) : PixivEntity {
    companion object SQL
}