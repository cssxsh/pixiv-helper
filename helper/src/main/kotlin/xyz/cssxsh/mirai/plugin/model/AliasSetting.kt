package xyz.cssxsh.mirai.plugin.model

import javax.persistence.*

@Entity
@Table(name = "statistic_alias")
data class AliasSetting(
    @Id
    @Column(name = "name", nullable = false)
    val alias: String = "",
    @Column(name = "uid", nullable = false)
    override val uid: Long = 0
) : PixivEntity, Author {
    companion object SQL
}