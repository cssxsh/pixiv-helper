package xyz.cssxsh.mirai.pixiv.model

import xyz.cssxsh.pixiv.*
import javax.persistence.*

@Entity
@Table(name = "artworks")
data class ArtWorkInfo(
    @Id
    @Column(name = "pid", nullable = false, updatable = false)
    val pid: Long = 0,
    @Column(name = "title", nullable = false, length = 32)
    val title: String = "",
    @Column(name = "caption", nullable = false)
    val caption: String = "",
    @Column(name = "create_at", nullable = false)
    val created: Long = 0,
    @Column(name = "page_count", nullable = false)
    val pages: Int = 0,
    @Column(name = "sanity_level", nullable = false)
    val sanity: Int = SanityLevel.NONE.ordinal,
    @Column(name = "type", nullable = false)
    val type: Int = WorkContentType.ILLUST.ordinal,
    @Column(name = "width", nullable = false)
    val width: Int = 0,
    @Column(name = "height", nullable = false)
    val height: Int = 0,
    @Column(name = "total_bookmarks", nullable = false)
    val bookmarks: Long = 0,
    @Column(name = "total_comments", nullable = false)
    val comments: Long = 0,
    @Column(name = "total_view", nullable = false)
    val view: Long = 0,
    @Column(name = "age", nullable = false)
    val age: Int = 0,
    @Column(name = "is_ero", nullable = false)
    val ero: Boolean = false,
    @Column(name = "deleted", nullable = false, updatable = false)
    val deleted: Boolean = true,
    @ManyToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "uid", nullable = false, updatable = false)
    val author: UserBaseInfo = UserBaseInfo()
) : PixivEntity {
    @ManyToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinTable(
        name = "artwork_tag",
        joinColumns = [JoinColumn(name = "pid", referencedColumnName = "pid")],
        inverseJoinColumns = [JoinColumn(name = "tid", referencedColumnName = "tid")]
    )
    var tags: List<TagRecord> = emptyList()

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "pid", insertable = false, updatable = false)
    var files: List<FileInfo> = emptyList()

    companion object SQL
}





