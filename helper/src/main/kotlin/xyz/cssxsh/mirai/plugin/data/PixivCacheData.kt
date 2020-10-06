package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.isR18
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

    fun add(illust: IllustInfo) = synchronized(illusts) {
        PixivHelperPlugin.logger.verbose("作品${illust.pid}信息将保存, 目前共${illusts.size}条信息")
        illusts[illust.pid] = illust
        if (illust.isEro()) ero.add(illust)
    }

    fun remove(pid: Long) = synchronized(illusts) {
        illusts.remove(pid)?.also { illust ->
            PixivHelperPlugin.logger.verbose("作品${illust.pid}信息将移除, 目前共${illusts.size}条信息")
            if (illust.isEro()) ero.remove(illust)
        }
    }

    private fun IllustInfo.isEro() =
        totalBookmarks ?: 0 >= 1000 && sanityLevel > 3 && isR18().not() && pageCount == 1

    val ero: MutableList<IllustInfo> by lazy {
        values.filter { it.isEro() }.also {
            logger.verbose("色图集初始化，共${it.size}张色图")
        }.toMutableList()
    }
}