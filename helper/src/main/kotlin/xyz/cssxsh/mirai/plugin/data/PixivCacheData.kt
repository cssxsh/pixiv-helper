package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperManager
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.pixiv.data.app.IllustInfo

object PixivCacheData : AutoSavePluginData(), PixivHelperLogger {
    /**
     * 缓存
     */
    private val illusts: MutableMap<Long, IllustInfo> by value(mutableMapOf())

    val values: List<IllustInfo> get() = synchronized(illusts) {
        illusts.values.toList()
    }

    operator fun contains(pid: Long) = synchronized(illusts) {
        illusts.contains(pid)
    }

    fun add(illustInfo: IllustInfo) = synchronized(illusts) {
        PixivHelperPlugin.logger.verbose("作品${illustInfo.pid}信息将保存, 目前共${illusts.size}条信息")
        illusts[illustInfo.pid] = illustInfo
    }

    fun remove(pid: Long) = synchronized(illusts) {
        illusts.remove(pid)?.also { illust ->
            PixivHelperPlugin.logger.verbose("作品${illust.pid}信息将移除, 目前共${illusts.size}条信息")
        }
    }
}