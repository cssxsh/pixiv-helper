package xyz.cssxsh.mirai.pixiv.model

import javax.persistence.*

@Entity
@Table(name = "tag")
data class TagRecord(
    @Id
    @Column(name = "name", nullable = false, length = 30, updatable = false)
    val name: String = "",
    @Column(name = "translated_name", nullable = true)
    val translated: String? = null,
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tid", nullable = false, updatable = false, insertable = false)
    val tid: Long = 0
) : PixivEntity {
    companion object SQL
}