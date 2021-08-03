package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*

object ImageSearchConfig : ReadOnlyPluginConfig("ImageSearchConfig") {
    @ValueDescription("请到 https://saucenao.com/user.php 获取")
    val key by value("")
}