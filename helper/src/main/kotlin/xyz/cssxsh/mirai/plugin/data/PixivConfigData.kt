package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

object PixivConfigData : AutoSavePluginConfig("PixivConfig") {

    /**
     * 默认助手配置
     */
    @ValueName("default_token")
    var default: String by value("")

    /**
     * 特定助手配置
     */
    @ValueName("tokens")
    val tokens: MutableMap<Long, String> by value(mutableMapOf())

    /**
     * 作品信息是否为简单构造
     */
    @ValueName("link")
    val link: MutableMap<String, Boolean> by value(mutableMapOf())

    /**
     * 连续发送间隔时间，单位秒
     */
    @ValueName("interval")
    var interval: Int by value(10)

    @ValueName("netdisk_access")
    var netdiskAccessToken: String by value("")

    @ValueName("netdisk_refresh")
    var netdiskRefreshToken: String by value("")

    @ValueName("netdisk_expires")
    var netdiskExpires: Long by value(0L)
}