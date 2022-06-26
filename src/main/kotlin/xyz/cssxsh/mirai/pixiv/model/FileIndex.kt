package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Embeddable
public data class FileIndex(
    @Column(name = "pid", nullable = false, updatable = false)
    val pid: Long,
    @Column(name = "`index`", nullable = false, updatable = false)
    val index: Int,
) : PixivEntity
