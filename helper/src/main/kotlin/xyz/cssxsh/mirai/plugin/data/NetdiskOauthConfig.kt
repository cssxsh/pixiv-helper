package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*
import xyz.cssxsh.baidu.*

object NetdiskOauthConfig : ReadOnlyPluginConfig("NetdiskOauthConfig"), BaiduAuthConfig {
    @ValueName("app_id")
    override val appId: Long by value(0L)

    @ValueName("app_key")
    override val appKey: String by value("")

    @ValueName("app_name")
    override val appName: String by value("")

    @ValueName("app_secret")
    override val secretKey: String by value("")
}