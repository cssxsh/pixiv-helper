package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.utils.secondsToMillis
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import java.io.File

object PixivHelperSettings : AutoSavePluginConfig("HelperSettings") {
    /**
     * 色图间隔
     */
    var minInterval: Int by value(16)

    /**
     * 图片缓存位置
     */
    var cachePath: String by value()

    /**
     * 缓存延迟时间
     */
    var delayTime: Long by value(1.secondsToMillis)


    /**
     * 缓存目录
     */
    val cacheFolder: File get() = if (cachePath.isEmpty()) {
        File(PixivHelperPlugin.dataFolder, "cache").apply { mkdir() }
    } else {
        File(cachePath).apply { mkdir() }
    }

    /**
     * 图片目录
     */
    fun imagesFolder(pid: Long): File = File(cacheFolder, pid.toString()).apply { mkdir() }
}