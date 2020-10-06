package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.pixiv.client.PixivConfig
import xyz.cssxsh.pixiv.data.AuthResult

object PixivHelperData : AutoSavePluginData() {
    /**
     * 用于存储认证消息
     */
    var authInfo: AuthResult.AuthInfo? by value(null)

    /**
     * 助手配置
     */
    var config: PixivConfig by value()

    /**
     * 作品信息是否为简单构造
     */
    val simpleInfo: MutableMap<Long, Boolean> by value()
}