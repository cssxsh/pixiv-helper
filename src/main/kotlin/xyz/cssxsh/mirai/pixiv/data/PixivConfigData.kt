package xyz.cssxsh.mirai.pixiv.data

import net.mamoe.mirai.console.data.*

object PixivConfigData : AutoSavePluginConfig("PixivConfig"), PixivHelperConfig {
    @ValueName("default_token")
    @ValueDescription("默认助手配置")
    var default: String by value("")

    @ValueName("tokens")
    @ValueDescription("特定助手配置")
    val tokens: MutableMap<Long, String> by value()

    @ValueName("link")
    @ValueDescription("是否显示原图链接")
    val link: MutableMap<String, Boolean> by value()

    @ValueName("tag")
    @ValueDescription("是否显示Tag信息")
    val tag: MutableMap<String, Boolean> by value()

    @ValueName("attr")
    @ValueDescription("是否显示Attr信息")
    val attr: MutableMap<String, Boolean> by value()

    @ValueName("pages")
    @ValueDescription("发送图片页数")
    val max: MutableMap<String, Int> by value()

    @ValueName("model")
    @ValueDescription("发送模式 NORMAL, FLASH, RECALL, FORWARD")
    val model: MutableMap<String, SendModel> by value()

    @ValueName("interval")
    @ValueDescription("task连续发送间隔时间，单位秒")
    var interval: Int by value(10)

    @ValueName("forward")
    @ValueDescription("task通过转发发送")
    var forward: Boolean by value(true)
}