package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.data.PluginDataExtensions.mapKeys
import org.sqlite.SQLiteConfig
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.pixiv.*
import java.io.File

object PixivHelperSettings : ReadOnlyPluginConfig("PixivHelperSettings"), EroStandardConfig {

    @ValueName("cache_path")
    @ValueDescription("缓存目录")
    private val cachePath: String by value("")

    @ValueName("backup_path")
    @ValueDescription("备份目录")
    private val backupPath: String by value("")

    @ValueName("temp_path")
    @ValueDescription("临时目录")
    private val tempPath: String by value("")

    @ValueName("i.pximg.net 反向代理 可以使用 i.pixiv.cat")
    val pximg: String by value("")

    @ValueName("ero_interval")
    @ValueDescription("色图间隔")
    val eroInterval: Int by value(ERO_INTERVAL)

    @ValueName("ero_work_types")
    @ValueDescription("涩图标准 内容类型 ILLUST, UGOIRA, MANGA, 为空则全部符合")
    private val types_: Set<String> by value(setOf())

    @ValueName("ero_work_types")
    @ValueDescription("涩图标准 内容类型 ILLUST, UGOIRA, MANGA, 为空则全部符合")
    @Suppress("internal")
    override val types: Set<WorkContentType> by lazy { types_.map { WorkContentType.valueOf(it.uppercase()) }.toSet() }

    @ValueName("ero_bookmarks")
    @ValueDescription("涩图标准 收藏")
    override val bookmarks: Long by value(ERO_BOOKMARKS)

    @ValueName("ero_page_count")
    @ValueDescription("涩图标准 页数")
    override val pages: Int by value(ERO_PAGE_COUNT)

    @ValueName("ero_tag_exclude")
    @ValueDescription("涩图标准 排除的正则表达式")
    override val tagExclude: String by value(ERO_TAG_EXCLUDE)

    @ValueName("ero_user_exclude")
    @ValueDescription("涩图标准 排除的UID")
    override val userExclude: Set<Long> by value(emptySet())

    @ValueName("sqlite_database")
    @ValueDescription("数据库文件位置")
    private val sqliteDatabase: String by value("")

    @ValueName("proxy")
    @ValueDescription("代理")
    val proxy: String by value("")

    @ValueName("sqlite_config")
    @ValueDescription("数据库配置")
    val sqliteConfig: Map<SQLiteConfig.Pragma, String> by value<Map<String, String>>()
        .mapKeys({ SQLiteConfig.Pragma.valueOf(it) }, { it.name })

    private fun getPath(path: String, default: String) =
        if (path.isEmpty()) PixivHelperPlugin.dataFolder.resolve(default) else File(".").resolve(path)

    /**
     * 压缩文件保存目录
     */
    val backupFolder: File get() = getPath(path = backupPath, default = "backup")

    /**
     * 图片缓存保存目录
     */
    val cacheFolder: File get() = getPath(path = cachePath, default = "cache")

    /**
     * 临时文件保存目录
     */
    val tempFolder: File get() = getPath(path = tempPath, default = "temp")

    /**
     * 用户文件保存目录
     */
    val profilesFolder: File get() = cacheFolder.resolve("profile")

    /**
     * 特辑文件保存目录
     */
    val articlesFolder: File get() = cacheFolder.resolve("article")

    /**
     * 图片目录
     */
    fun imagesFolder(pid: Long): File = cacheFolder
        .resolve("%03d______".format(pid / 1_000_000))
        .resolve("%06d___".format(pid / 1_000))
        .resolve("$pid")

    /**
     * 数据库文件路径
     */
    val sqlite: File get() = getPath(sqliteDatabase, "pixiv.sqlite")
}