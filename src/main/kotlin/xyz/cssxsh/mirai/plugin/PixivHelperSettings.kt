package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.plugin.jvm.loadSetting
import net.mamoe.mirai.console.setting.Setting
import net.mamoe.mirai.console.setting.getValue
import net.mamoe.mirai.console.setting.value
import xyz.cssxsh.mirai.plugin.setting.PixivClientSetting

object PixivHelperSettings: Setting by PixivHelperMain.loadSetting() {
    val proxy: String by value("")
    val groups: MutableMap<Long, PixivClientSetting> by value(hashMapOf())
    val users: MutableMap<Long, PixivClientSetting> by value(hashMapOf())
}