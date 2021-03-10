package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.PluginDataExtensions.mapKeys
import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import org.sqlite.SQLiteConfig
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.dataFolder
import java.io.File

object PixivHelperSettings : ReadOnlyPluginConfig("PixivHelperSettings") {

    @ValueName("cache_path")
    private val cachePath: String by value("")

    @ValueName("backup_path")
    private val backupPath: String by value("")

    @ValueName("temp_path")
    private val tempPath: String by value("")

    /**
     * 色图间隔
     */
    @ValueName("ero_interval")
    val eroInterval: Int by value(ERO_INTERVAL)

    /**
     * 涩图标准 收藏
     */
    @ValueName("ero_bookmarks")
    val eroBookmarks: Long by value(ERO_BOOKMARKS)

    /**
     * 涩图标准 页数
     */
    @ValueName("ero_page_count")
    val eroPageCount: Int by value(ERO_PAGE_COUNT)

    @ValueName("sqlite_database")
    private val sqliteDatabase: String by value("")

    /**
     * 数据库配置
     */
    @ValueName("sqlite_config")
    val sqliteConfig: Map<SQLiteConfig.Pragma, String> by value<Map<String, String>>()
        .mapKeys({ SQLiteConfig.Pragma.valueOf(it) }, { it.name })

    private fun getPath(path: String, default: String) =
        if (path.isEmpty()) { dataFolder.resolve(default) } else { File(".").resolve(path) }

    /**
     * 压缩文件保存目录
     */
    val backupFolder: File
        get() = getPath(path = backupPath, default = "backup")

    /**
     * 图片缓存保存目录
     */
    val cacheFolder: File
        get() = getPath(path = cachePath, default = "cache")

    /**
     * 临时文件保存目录
     */
    val tempFolder: File
        get() = getPath(path = tempPath, default = "temp")

    /**
     * 用户文件保存目录
     */
    val profilesFolder: File
        get() = cacheFolder.resolve("profile")

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
    val sqlite: File
        get() = getPath(sqliteDatabase, "pixiv.sqlite")
}