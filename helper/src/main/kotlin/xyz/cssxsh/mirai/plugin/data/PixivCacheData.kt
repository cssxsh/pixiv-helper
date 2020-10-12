package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.BaseInfo.Companion.toBaseInfo
import xyz.cssxsh.pixiv.data.app.IllustInfo

object PixivCacheData : AutoSavePluginData("PixivCache"), PixivHelperLogger {

    @ConsoleExperimentalApi
    override fun shouldPerformAutoSaveWheneverChanged() = false

    /**
     * 缓存
     */
    private val illusts: MutableMap<Long, BaseInfo> by value(mutableMapOf())

    private val eros_illusts: MutableSet<Long> by value(mutableSetOf())

    private val r18s_illusts: MutableSet<Long> by value(mutableSetOf())

    fun caches() = synchronized(illusts) { illusts.toMap() }

    fun eros() = synchronized(illusts) {
        eros_illusts.map {
            requireNotNull(illusts[it]) { "缓存错误" }
        }
    }

    fun r18s() = synchronized(illusts) {
        r18s_illusts.map {
            requireNotNull(illusts[it]) { "缓存错误" }
        }
    }

    /**
     * 筛选出不在缓存里的部分
     * @return 不包含在已缓存的数据中的部分
     */
    fun update(list: List<IllustInfo>): Map<Long, IllustInfo> = buildMap {
        synchronized(illusts) {
            list.forEach {
                if (it.pid !in illusts) {
                    put(it.pid, it)
                } else {
                    illusts[it.pid] = it.toBaseInfo()
                }
            }
        }
    }

    operator fun contains(pid: Long) = synchronized(illusts) {
        illusts.contains(pid)
    }

    private fun put0(illust: IllustInfo) = illusts.put(illust.pid, illust.toBaseInfo()).also {
        logger.info("作品(${illust.pid})<${illust.createDate.format("yyyy-MM-dd")}>[${illust.title}]{${illust.totalBookmarks}}信息已设置, 目前共${illusts.size}条信息")
        if (illust.isEro()) {
            if (illust.isR18()) {
                r18s_illusts.add(illust.pid)
            } else {
                eros_illusts.add(illust.pid)
            }
        }
    }

    fun putAll(list: Collection<IllustInfo>) = synchronized(illusts) {
        list.map { illust -> put0(illust) }
    }

    fun put(illust: IllustInfo) = synchronized(illusts) {
        put0(illust)
    }

    fun remove(illust: IllustInfo) = remove(illust.pid)

    fun remove(pid: Long) = synchronized(illusts) {
        illusts.remove(pid)?.also { illust ->
            logger.info("作品(${illust.pid})<${illust.createDate.format("yyyy-MM-dd")}>[${illust.title}]{${illust.totalBookmarks}}信息已移除, 目前共${illusts.size}条信息")
            if (illust.isEro()) {
                eros_illusts.remove(illust.pid)
                r18s_illusts.remove(illust.pid)
            }
        }
    }
}