package xyz.cssxsh.mirai.pixiv.model

import javax.persistence.*

@Entity
@Table(name = "artwork_tag")
data class ArtworkTag(
    @Id
    @Column(name = "pid", nullable = false, updatable = false)
    val pid: Long,
    @Id
    @OneToOne
    @JoinColumn(name = "tid", referencedColumnName = "tid", nullable = false, updatable = false)
    val tag: TagRecord,
) : PixivEntity {
    companion object SQL
}