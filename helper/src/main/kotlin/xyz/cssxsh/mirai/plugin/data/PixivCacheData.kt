package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.isEro
import xyz.cssxsh.mirai.plugin.isR18
import xyz.cssxsh.mirai.plugin.readIllustInfo
import xyz.cssxsh.pixiv.data.app.IllustInfo
import java.io.File

object PixivCacheData : AutoSavePluginData("PixivCache"), PixivHelperLogger {
    /**
     * 缓存
     */
    private val illusts: MutableSet<Long> by value(mutableSetOf())

    val eros: MutableMap<Long, IllustInfo> by value(mutableMapOf())

    val r18s: MutableMap<Long, IllustInfo> by value(mutableMapOf())

    val size: Int get() = synchronized(illusts) { illusts.size }

    fun values(): Map<Long, IllustInfo?> = buildMap {
        synchronized(illusts) {
            illusts.forEach { pid ->
                val file = File(PixivHelperSettings.imagesFolder(pid), "${pid}.json")
                runCatching {
                    file.readIllustInfo()
                }.onFailure {
                    logger.warning("${file.absolutePath} 读取失败")
                }.let {
                    put(pid, it.getOrNull())
                }
            }
        }
    }

    /**
     * 筛选出不在缓存里的部分
     */
    fun filter(list: List<IllustInfo>): Map<Long, IllustInfo> = buildMap {
        synchronized(illusts) {
            list.forEach {
                if (illusts.contains(it.pid).not()) {
                    put(it.pid, it)
                }
            }
        }
    }

    operator fun contains(pid: Long) = synchronized(illusts) {
        illusts.contains(pid)
    }

    fun add(illust: IllustInfo) = synchronized(illusts) {
        if (illusts.add(illust.pid)) {
            logger.info("作品(${illust.pid})<${illust.createDate.format("yyyy-MM-dd")}>[${illust.title}]{${illust.totalBookmarks}}信息已添加, 目前共${illusts.size}条信息")
            if (illust.isEro()) {
                if (illust.isR18()) {
                    r18s[illust.pid] = illust
                } else {
                    eros[illust.pid] = illust
                }
            }
        }
    }

    fun remove(illust: IllustInfo) = synchronized(illusts) {
        if (illusts.remove(illust.pid)) {
            logger.info("作品(${illust.pid})<${illust.createDate.format("yyyy-MM-dd")}>[${illust.title}]{${illust.totalBookmarks}}信息已移除, 目前共${illusts.size}条信息")
            if (illust.isEro()) {
                eros.remove(illust.pid)
                r18s.remove(illust.pid)
            }
        }
    }
}