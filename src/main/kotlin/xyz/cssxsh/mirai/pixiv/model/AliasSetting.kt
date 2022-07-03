package xyz.cssxsh.mirai.pixiv.model

import jakarta.persistence.*

@Entity
@Table(name = "statistic_alias")
public data class AliasSetting(
    @Id
    @Column(name = "name", nullable = false)
    val alias: String,
    @Column(name = "uid", nullable = false)
    override val uid: Long
) : PixivEntity, Author {
    public companion object SQL
}