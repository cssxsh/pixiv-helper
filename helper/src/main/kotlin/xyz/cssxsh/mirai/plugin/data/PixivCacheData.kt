package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.isR18
import xyz.cssxsh.pixiv.data.app.IllustInfo

object PixivCacheData : AutoSavePluginData("PixivCache"), PixivHelperLogger {
    /**
     * 缓存
     */
    private val illusts: MutableSet<Long> by value(mutableSetOf())

    val eros: MutableMap<Long, IllustInfo> by value(mutableMapOf())

    private fun IllustInfo.isEro() = totalBookmarks ?: 0 >= 1000 && sanityLevel > 2 && isR18().not() && pageCount < 4

    val values: Set<Long> get() = synchronized(illusts) {
        illusts.toSet()
    }

    /**
     * 筛选出不在缓存里的部分
     */
    fun filter(list: List<IllustInfo>): List<IllustInfo> = synchronized(illusts) {
        list.filter {
            illusts.contains(it.pid).not()
        }
    }

    operator fun contains(pid: Long) = synchronized(illusts) {
        illusts.contains(pid)
    }

    fun add(illust: IllustInfo) = synchronized(illusts) {
        if (illusts.add(illust.pid)) {
            logger.info("作品(${illust.pid})[${illust.title}]{${illust.pageCount}}信息将添加, 目前共${illusts.size}条信息")
            if (illust.isEro()) eros[illust.pid] = illust
        }
    }

    fun remove(illust: IllustInfo) = synchronized(illusts) {
        if (illusts.remove(illust.pid)) {
            logger.info("作品(${illust.pid})[${illust.title}]{${illust.pageCount}}信息将移除, 目前共${illusts.size}条信息")
            if (illust.isEro()) eros.remove(illust.pid)
        }
    }
}