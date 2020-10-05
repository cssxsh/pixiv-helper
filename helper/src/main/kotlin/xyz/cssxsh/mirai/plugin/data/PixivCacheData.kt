package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.pixiv.data.app.IllustInfo

object PixivCacheData : AutoSavePluginData() {
    /**
     * 缓存
     */
    val illusts: MutableMap<Long, IllustInfo> by value(mutableMapOf())

    fun add(illustInfo: IllustInfo) = illusts.set(illustInfo.pid.also {
        PixivHelperPlugin.logger.verbose("作品${illustInfo.pid}信息将保存, 目前共${illusts.size}条信息")
    }, illustInfo)
}