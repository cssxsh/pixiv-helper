package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object ImageSearchConfig : ReadOnlyPluginConfig("ImageSearchConfig") {
    @ValueDescription("请到 https://saucenao.com/user.php 获取")
    val key by value("")
}