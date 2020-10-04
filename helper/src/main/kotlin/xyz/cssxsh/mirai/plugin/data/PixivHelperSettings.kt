package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*

object PixivHelperSettings : AutoSavePluginConfig() {
    /**
     * 客户端的默认代理
     *
     * TODO：修改时推送到每个客户端
     */
    var proxy: String? by value()
}