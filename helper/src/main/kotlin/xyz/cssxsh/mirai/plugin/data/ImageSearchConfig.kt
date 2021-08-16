package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*

object ImageSearchConfig : ReadOnlyPluginConfig("ImageSearchConfig") {
    @ValueDescription("请到 https://saucenao.com/user.php 获取")
    val key by value("")

    @ValueDescription("搜索显示的结果个数")
    val limit by value(3)

    @ValueDescription("ascii2d 检索类型，false色合検索 true特徴検索")
    val bovw by value(true)
}