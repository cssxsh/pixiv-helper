package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "artwork_tag")
public data class ArtWorkTag(
    @Id
    @Column(name = "pid", nullable = false, updatable = false)
    val pid: String,
    @Column(name = "tid", nullable = false, updatable = false)
    val tid: Long = 0
) : PixivEntity {
    public companion object SQL
}