package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.DEFAULT_PIXIV_CONFIG
import xyz.cssxsh.pixiv.PixivConfig

object PixivConfigData : AutoSavePluginConfig("PixivConfig") {

    /**
     * 默认助手配置
     */
    @ValueName("default")
    var default: PixivConfig by value(DEFAULT_PIXIV_CONFIG)

    /**
     * 特定助手配置
     */
    @ValueName("configs")
    val configs: MutableMap<Long, PixivConfig> by value(mutableMapOf())

    /**
     * 作品信息是否为简单构造
     */
    @ValueName("link")
    val link: MutableMap<String, Boolean> by value(mutableMapOf())

    @ValueName("netdisk_access")
    var netdiskAccessToken: String by value("")

    @ValueName("netdisk_refresh")
    var netdiskRefreshToken: String by value("")

    @ValueName("netdisk_expires")
    var netdiskExpires: Long by value(0L)
}