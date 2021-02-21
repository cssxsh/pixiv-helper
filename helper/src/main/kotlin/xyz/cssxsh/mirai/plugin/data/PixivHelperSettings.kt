package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.PluginDataExtensions.mapKeys
import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import org.sqlite.SQLiteConfig
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.dataFolder
import xyz.cssxsh.mirai.plugin.tools.BaiduPanUpdater
import java.io.File

object PixivHelperSettings : ReadOnlyPluginConfig("HelperSettings") {

    /**
     * 图片缓存位置
     */
    @ValueName("cache_path")
    private val cachePath: String by value("")

    /**
     * 压缩文件保存目录
     */
    @ValueName("backup_path")
    private val backupPath: String by value("")

    /**
     * 色图间隔
     */
    @ValueName("ero_interval")
    val eroInterval: Int by value(16)

    /**
     * 涩图标准
     */
    @ValueName("ero_bookmarks")
    val eroBookmarks: Long by value(10_000L)

    @ValueName("ero_page_count")
    val eroPageCount: Int by value(5)

    /**
     * 百度云
     */
    @ValueName("pan_config")
    val panConfig: BaiduPanUpdater.UserConfig by value(BaiduPanUpdater.UserConfig(
        logId = "",
        targetPath = "/Pixiv",
        cookies = emptyList()
    ))

    @ValueName("sqlite_database")
    private val sqliteDatabase: String by value("./pixiv.sqlite")

    @ValueName("sqlite_config")
    val sqliteConfig: Map<SQLiteConfig.Pragma, String> by value<Map<String, String>>()
        .mapKeys({ SQLiteConfig.Pragma.valueOf(it) }, { it.name })

    /**
     * 缓存目录
     */
    val cacheFolder: File
        get() = if (cachePath.isEmpty()) {
            dataFolder.resolve("cache")
        } else {
            File(".").resolve(cachePath)
        }

    /**
     * 压缩文件保存目录
     */
    val backupFolder: File
        get() = if (backupPath.isEmpty()) {
            dataFolder.resolve("backup")
        } else {
            File(".").resolve(backupPath)
        }

    /**
     * 图片目录
     */
    fun imagesFolder(pid: Long): File = cacheFolder
        .resolve("%03d______".format(pid / 1_000_000))
        .resolve("%06d___".format(pid / 1_000))
        .resolve("$pid")

    val sqlite = File(".").resolve(sqliteDatabase).also { sqlite ->
        if (sqlite.exists().not()) {
            this@PixivHelperSettings::class.java.getResourceAsStream("pixiv.sqlite")?.use {
                sqlite.writeBytes(it.readAllBytes())
            }
        }
    }
}