package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import org.sqlite.JDBC
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.dataFolder
import xyz.cssxsh.mirai.plugin.tools.PanConfig
import java.io.File
import kotlin.time.seconds

object PixivHelperSettings : ReadOnlyPluginConfig("HelperSettings") {
    /**
     * 色图间隔
     */
    @ValueName("min_interval")
    val minInterval: Int by value(16)

    /**
     * 图片缓存位置
     */
    @ValueName("cache_path")
    val cachePath: String by value("")

    /**
     * 压缩文件保存目录
     */
    @ValueName("backup_path")
    val backupPath: String by value("")

    /**
     * 涩图标准
     */
    @ValueName("total_bookmarks")
    val totalBookmarks: Long by value(10_000L)

    /**
     * 百度云
     */
    @ValueName("pan_config")
    val panConfig: PanConfig by value(PanConfig(
        bdsToken = "a25846e85d9ae2b6356b1c78d31c9ef3",
        logId = "MjA3ODZFQzFBMUNFODVDRjdFRkVBMUZGMkZBOTdBM0Y6Rkc9MQ==",
        targetPath = "/Pixiv",
        cookies = "BDUSS=JnNUVzZTBIRjBxSm10dTVtQ01Mb01nNDNhYkxzck5hTVZsRH5GRGJTdzhyNjFmSUFBQUFBJCQAAAAAAAAAAAEAAADUV~MytLTKwMnx0KHJ-ruvAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADwihl88IoZfTz; BDUSS_BFESS=JnNUVzZTBIRjBxSm10dTVtQ01Mb01nNDNhYkxzck5hTVZsRH5GRGJTdzhyNjFmSUFBQUFBJCQAAAAAAAAAAAEAAADUV~MytLTKwMnx0KHJ-ruvAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADwihl88IoZfTz; STOKEN=0119324ef6417dfc810b4da1c76267b37d3e50d3d6a72f345ff751a453f2a93f"
    ))


    /**
     * 缓存目录
     */
    val cacheFolder: File
        get() = if (cachePath.isEmpty()) {
            dataFolder.resolve("cache")
        } else {
            File(cachePath)
        }.apply { mkdir() }

    /**
     * 压缩文件保存目录
     */
    val backupFolder: File
        get() = if (backupPath.isEmpty()) {
            dataFolder.resolve("backup")
        } else {
            File(backupPath)
        }.apply { mkdir() }

    /**
     * 图片目录
     */
    fun imagesFolder(pid: Long): File = cacheFolder
        .resolve("%03d______".format(pid / 1_000_000))
        .resolve("%06d___".format(pid / 1_000))
        .resolve("$pid")

    val sqliteUrl: String
        get() =
            "${JDBC.PREFIX}${File(".").resolve("pixiv.sqlite").absolutePath}"
}