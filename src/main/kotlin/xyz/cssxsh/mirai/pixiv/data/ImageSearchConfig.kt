package xyz.cssxsh.mirai.pixiv.data

import net.mamoe.mirai.console.data.*

public object ImageSearchConfig : ReadOnlyPluginConfig("ImageSearchConfig"), PixivHelperConfig {
    @ValueDescription("请到 https://saucenao.com/user.php 获取")
    public val key: String by value("")

    @ValueDescription("搜索显示的结果个数")
    public val limit: Int by value(3)

    @ValueDescription("ascii2d 检索类型，false 色合検索 true 特徴検索")
    public val bovw: Boolean by value(true)

    @ValueDescription("图片等待时间，单位秒")
    public val wait: Int by value(300)

    @ValueDescription("转发方式发送搜索结果")
    public val forward: Boolean by value(false)
}