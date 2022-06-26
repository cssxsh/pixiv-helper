package xyz.cssxsh.mirai.pixiv.data

import net.mamoe.mirai.console.data.*

public object PixivConfigData : AutoSavePluginConfig("PixivConfig") {

    @ValueName("link")
    @ValueDescription("是否显示原图链接")
    public val link: MutableMap<Long, Boolean> by value()

    @ValueName("tag")
    @ValueDescription("是否显示Tag信息")
    public val tag: MutableMap<Long, Boolean> by value()

    @ValueName("attr")
    @ValueDescription("是否显示Attr信息")
    public val attr: MutableMap<Long, Boolean> by value()

    @ValueName("pages")
    @ValueDescription("发送图片页数")
    public val max: MutableMap<Long, Int> by value()

    @ValueName("model")
    @ValueDescription("发送模式 NORMAL, FLASH, RECALL, FORWARD")
    public val model: MutableMap<Long, SendModel> by value()

    @ValueName("interval")
    @ValueDescription("task连续发送间隔时间，单位秒")
    public var interval: Int by value(10)

    @ValueName("forward")
    @ValueDescription("task通过转发发送")
    public var forward: Boolean by value(true)
}