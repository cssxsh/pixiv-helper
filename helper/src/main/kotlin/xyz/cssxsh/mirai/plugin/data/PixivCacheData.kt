package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.info
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.pixiv.data.BaseInfo
import xyz.cssxsh.pixiv.data.BaseInfo.Companion.toBaseInfo
import xyz.cssxsh.pixiv.data.app.IllustInfo

object PixivCacheData : AutoSavePluginData("PixivCache"), PixivHelperLogger {

    @ConsoleExperimentalApi
    override fun shouldPerformAutoSaveWheneverChanged(): Boolean = false

    @ValueName("illusts")
    private val illusts: MutableMap<Long, BaseInfo> by value(mutableMapOf())

    @ValueName("ero_illusts")
    private val eroIllusts: MutableSet<Long> by value(mutableSetOf())

    @ValueName("r18_illusts")
    private val r18Illusts: MutableSet<Long> by value(mutableSetOf())

    val size get() = illusts.size

    fun toMap() = synchronized(illusts) { illusts.toMap() }

    fun filter(predicate: (Map.Entry<Long, BaseInfo>) -> Boolean) = synchronized(illusts) { illusts.filter(predicate) }

    fun count(predicate: (Map.Entry<Long, BaseInfo>) -> Boolean) = synchronized(illusts) { illusts.count(predicate) }

    fun eros(predicate: (BaseInfo) -> Boolean = { true }) = synchronized(illusts) {
        eroIllusts.mapNotNull { pid ->
            requireNotNull(illusts[pid]) { "${pid}缓存错误" }.takeIf {
                predicate(it)
            }
        }
    }

    fun r18s(predicate: (BaseInfo) -> Boolean = { true }) = synchronized(illusts) {
        r18Illusts.mapNotNull { pid ->
            requireNotNull(illusts[pid]) { "${pid}缓存错误" }.takeIf {
                predicate(it)
            }
        }
    }

    private fun BaseInfo.toInfo() =
        "(${pid})<${getCreateDateText()}>[${title}][${type}][${pageCount}]{${totalBookmarks}}"

    /**
     * 筛选出不在缓存里的部分
     * @return 不包含在已缓存的数据中的部分
     */
    fun update(list: List<IllustInfo>): Map<Long, IllustInfo> = buildMap {
        synchronized(illusts) {
            list.forEach { illust ->
                if (illust.pid !in illusts) {
                    put(illust.pid, illust)
                } else {
                    illusts[illust.pid] = illust.toBaseInfo()
                    if (illust.isEro()) {
                        if (illust.isR18()) {
                            r18Illusts.add(illust.pid)
                        } else {
                            eroIllusts.add(illust.pid)
                        }
                    }
                }
            }
        }
    }

    operator fun contains(pid: Long) = synchronized(illusts) {
        illusts.contains(pid)
    }

    private fun put0(illust: IllustInfo) = illusts.put(illust.pid, illust.toBaseInfo()).also {
        logger.info { "作品${illust.toBaseInfo().toInfo()}信息已${if (it == null) "设置" else "刷新"}, 目前共${illusts.size}条信息" }
        if (illust.isEro()) {
            if (illust.isR18()) {
                r18Illusts.add(illust.pid)
            } else {
                eroIllusts.add(illust.pid)
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
            logger.info { "作品${illust.toInfo()}信息已移除, 目前共${illusts.size}条信息" }
            if (illust.isEro()) {
                eroIllusts.remove(illust.pid)
                r18Illusts.remove(illust.pid)
            }
        }
    }
}