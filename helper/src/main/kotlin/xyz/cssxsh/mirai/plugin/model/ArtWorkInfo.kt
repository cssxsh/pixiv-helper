package xyz.cssxsh.mirai.plugin.model

import xyz.cssxsh.pixiv.*
import javax.persistence.*
import java.io.*

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
) {
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "pid", insertable = false, updatable = false)
    lateinit var tags: List<TagBaseInfo>

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "pid", insertable = false, updatable = false)
    lateinit var files: List<FileInfo>

    companion object SQL
}

@Entity
@Table(name = "files")
data class FileInfo(
    @Id
    @Column(name = "pid", nullable = false, updatable = false)
    val pid: Long = 0,
    @Id
    @Column(name = "`index`", nullable = false, updatable = false)
    val index: Int = 0,
    @Column(name = "md5", nullable = false, length = 32)
    val md5: String = "",
    @Column(name = "url", nullable = false)
    val url: String = "",
    @Column(name = "size", nullable = false)
    val size: Int = 0
) : Serializable {
    companion object SQL
}

@Entity
@Table(name = "tags")
data class TagBaseInfo(
    @Id
    @Column(name = "pid", nullable = false, updatable = false)
    val pid: Long = 0,
    @Id
    @Column(name = "name", nullable = false, length = 30, updatable = false)
    val name: String = "",
    @Column(name = "translated_name", nullable = true)
    val translated: String? = null
) : Serializable

@Entity
@Table(name = "users")
data class UserBaseInfo(
    @Id
    @Column(name = "uid", nullable = false, updatable = false)
    val uid: Long = 0,
    @Column(name = "name", nullable = false, length = 15)
    val name: String = "",
    @Column(name = "account", nullable = false, length = 32)
    val account: String = ""
) {
    companion object SQL
}

@Entity
@Table(name = "twitter")
data class Twitter(
    @Id
    @Column(name = "screen", nullable = false, length = 50)
    val screen: String = "",
    @Column(name = "uid", nullable = false, updatable = false)
    val uid: Long,
) {
    companion object SQL
}