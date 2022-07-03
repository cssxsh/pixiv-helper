package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "tag")
public data class TagRecord(
    @Id
    @Column(name = "name", nullable = false, length = 30, updatable = false)
    val name: String,
    @Column(name = "translated_name", nullable = true)
    val translated: String?,
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tid", nullable = false, updatable = false, insertable = false)
    val tid: Long = 0
) : PixivEntity {
    public companion object SQL
}