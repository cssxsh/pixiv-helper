package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "files")
public data class FileInfo(
    @Transient
    val pid: Long,
    @Transient
    val index: Int,
    @Column(name = "md5", nullable = false, length = 32)
    val md5: String,
    @Column(name = "url", nullable = false)
    val url: String,
    @Column(name = "size", nullable = false)
    val size: Int
) : PixivEntity {
    @EmbeddedId
    val id: FileIndex = FileIndex(pid = pid, index = index)

    public companion object SQL
}