package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.pixiv.client.PixivConfig

object PixivConfigData : AutoSavePluginConfig("PixivConfig") {

    /**
     * 默认助手配置
     */
    @ValueName("default")
    var default: PixivConfig by value()

    /**
     * 特定助手配置
     */
    @ValueName("configs")
    val configs: MutableMap<Long, PixivConfig> by value(mutableMapOf())

    /**
     * 作品信息是否为简单构造
     */
    @ValueName("simple_info")
    val simpleInfo: MutableMap<String, Boolean> by value(mutableMapOf())
}