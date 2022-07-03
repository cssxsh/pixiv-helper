package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "tags")
public data class TagBaseInfo(
    @Id
    @Column(name = "pid", nullable = false, updatable = false)
    val pid: Long,
    @Id
    @Column(name = "name", nullable = false, length = 30, updatable = false)
    val name: String,
    @Column(name = "translated_name", nullable = true)
    val translated: String?
) : PixivEntity