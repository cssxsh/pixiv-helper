package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*

object PixivHelperSettings : AutoSavePluginConfig() {
    /**
     * 色图间隔
     */
    var minInterval: Int by value(16)

    /**
     * 图片缓存位置
     */
    var cachePath: String by value()
}