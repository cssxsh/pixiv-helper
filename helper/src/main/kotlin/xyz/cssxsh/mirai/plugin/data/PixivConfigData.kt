package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*

object PixivConfigData : AutoSavePluginConfig("PixivConfig") {
    @ValueName("default_token")
    @ValueDescription("默认助手配置")
    var default: String by value("")

    @ValueName("tokens")
    @ValueDescription("特定助手配置")
    val tokens: MutableMap<Long, String> by value(mutableMapOf())

    @ValueName("link")
    @ValueDescription("是否显示原图链接")
    val link: MutableMap<String, Boolean> by value(mutableMapOf())

    @ValueName("tag")
    @ValueDescription("是否显示Tag信息")
    val tag: MutableMap<String, Boolean> by value(mutableMapOf())

    @ValueName("attr")
    @ValueDescription("是否显示Attr信息")
    val attr: MutableMap<String, Boolean> by value(mutableMapOf())

    @ValueName("pages")
    @ValueDescription("发送图片页数")
    val max: MutableMap<String, Int> by value(mutableMapOf())

    @ValueName("model")
    @ValueDescription("发送模式")
    val model: MutableMap<String, SendModel> by value(mutableMapOf())

    @ValueName("interval")
    @ValueDescription("连续发送间隔时间，单位秒")
    var interval: Int by value(10)

    @ValueName("netdisk_access")
    @ValueDescription("百度网盘 访问TOKEN")
    var netdiskAccessToken: String by value("")

    @ValueName("netdisk_refresh")
    @ValueDescription("百度网盘 刷新TOKEN")
    var netdiskRefreshToken: String by value("")

    @ValueName("netdisk_expires")
    @ValueDescription("百度网盘 过期时间")
    var netdiskExpires: Long by value(0L)
}