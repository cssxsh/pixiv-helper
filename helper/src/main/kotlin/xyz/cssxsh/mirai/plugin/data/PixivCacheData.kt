package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.pixiv.data.app.IllustInfo

object PixivCacheData : AutoSavePluginData() {
    /**
     * 缓存
     */
    val illust: MutableMap<Long, IllustInfo> by value(mutableMapOf())

    fun  add(illustInfo: IllustInfo) = illust.set(illustInfo.pid, illustInfo)
}