package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.pixiv.data.app.IllustInfo

object PixivCacheData : AutoSavePluginData() {
    /**
     * 缓存
     */
    private val illusts: MutableMap<Long, IllustInfo> by value(mutableMapOf())

    val values get() = synchronized(illusts) {
        illusts.values
    }

    operator fun contains(pid: Long) = synchronized(illusts) {
        illusts.contains(pid)
    }

    fun add(illustInfo: IllustInfo) = synchronized(illusts) {
        PixivHelperPlugin.logger.verbose("作品${illustInfo.pid}信息将保存, 目前共${illusts.size}条信息")
        illusts[illustInfo.pid] = illustInfo
    }

    fun remove(pid: Long) = synchronized(illusts) {
        PixivHelperPlugin.logger.verbose("作品${pid}信息将移除, 目前共${illusts.size}条信息")
        illusts.remove(pid)
    }
}