package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import xyz.cssxsh.mirai.plugin.command.Method

object PixivHelperPlugin : KotlinPlugin() {
    // val qqId = 3337342367L // Bot的QQ号，需为Long类型，在结尾处添加大写L
    // val password = "66RKVt^eX&MfE7" // Bot的密码
    // login 3337342367 66RKVt^eX&MfE7
    override fun onEnable() {
        Method.register()
        PixivHelperSettings.reload()
        PixivHelperPluginData.reload()
    }

    override fun onDisable() {
        Method.unregister()
        PixivHelperManager.closeAll()
    }
}