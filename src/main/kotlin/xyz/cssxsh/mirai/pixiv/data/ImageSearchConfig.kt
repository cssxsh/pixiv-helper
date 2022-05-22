package xyz.cssxsh.mirai.pixiv.data

import net.mamoe.mirai.console.data.*

object ImageSearchConfig : ReadOnlyPluginConfig("ImageSearchConfig"), PixivHelperConfig {
    @ValueDescription("请到 https://saucenao.com/user.php 获取")
    val key by value("")

    @ValueDescription("搜索显示的结果个数")
    val limit by value(3)

    @ValueDescription("ascii2d 检索类型，false 色合検索 true 特徴検索")
    val bovw by value(true)

    @ValueDescription("图片等待时间，单位秒")
    val wait by value(300)

    @ValueDescription("转发方式发送搜索结果")
    val forward by value(false)
}