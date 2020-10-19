package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.utils.secondsToMillis
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.updater.PanConfig
import java.io.File

object PixivHelperSettings : AutoSavePluginConfig("HelperSettings") {
    /**
     * 色图间隔
     */
    var minInterval: Int by value(16)

    /**
     * tag总计最大
     */
    var maxTagCount: Int by value(16)

    /**
     * 图片缓存位置
     */
    var cachePath: String by value()

    /**
     * 压缩文件保存目录
     */
    var zipPath: String by value()

    /**
     * 缓存延迟时间
     */
    var delayTime: Long by value(1.secondsToMillis)

    /**
     * 涩图标准
     */
    var totalBookmarks: Long by value(10_000L)

    /**
     * 百度云
     */
    var panConfig: PanConfig by value(PanConfig(
        bdsToken = "a25846e85d9ae2b6356b1c78d31c9ef3",
        logId = "MjA3ODZFQzFBMUNFODVDRjdFRkVBMUZGMkZBOTdBM0Y6Rkc9MQ==",
        targetPath = "/Pixiv",
        cookies = "BDUSS=JnNUVzZTBIRjBxSm10dTVtQ01Mb01nNDNhYkxzck5hTVZsRH5GRGJTdzhyNjFmSUFBQUFBJCQAAAAAAAAAAAEAAADUV~MytLTKwMnx0KHJ-ruvAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADwihl88IoZfTz; BDUSS_BFESS=JnNUVzZTBIRjBxSm10dTVtQ01Mb01nNDNhYkxzck5hTVZsRH5GRGJTdzhyNjFmSUFBQUFBJCQAAAAAAAAAAAEAAADUV~MytLTKwMnx0KHJ-ruvAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADwihl88IoZfTz; STOKEN=0119324ef6417dfc810b4da1c76267b37d3e50d3d6a72f345ff751a453f2a93f"
    ))


    /**
     * 缓存目录
     */
    val cacheFolder: File get() = if (cachePath.isEmpty()) {
        File(PixivHelperPlugin.dataFolder, "cache").apply { mkdir() }
    } else {
        File(cachePath).apply { mkdir() }
    }

    /**
     * 压缩文件保存目录
     */
    val zipFolder: File get() = if (zipPath.isEmpty()) {
        File(PixivHelperPlugin.dataFolder, "zip").apply { mkdir() }
    } else {
        File(zipPath).apply { mkdir() }
    }

    /**
     * 图片目录
     */
    fun imagesFolder(pid: Long): File = cacheFolder
        .resolve("%03d______".format(pid / 1_000_000)).apply { mkdir() }
        .resolve("%06d___".format(pid / 1_000)).apply { mkdir() }
        .resolve("$pid").apply { mkdir() }
}