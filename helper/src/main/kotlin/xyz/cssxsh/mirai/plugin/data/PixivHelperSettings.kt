package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*
import xyz.cssxsh.pixiv.client.PixivConfig

object PixivHelperSettings : AutoSavePluginConfig() {
    /**
     * 色图间隔
     */
    var minInterval: Int by value(16)

    /**
     * 图片缓存位置
     */
    var cachePath: String by value()

    /**
     * 助手配置
     */
    var config: PixivConfig by value()
}