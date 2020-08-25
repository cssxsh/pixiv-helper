package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.command.PluginCommandOwner
import xyz.cssxsh.mirai.plugin.command.ShowSetting

object PixivHelperMain: KotlinPlugin() {

    // XXX: 这个 PluginCommandOwner 可能会在 1.0-M3 修改.
    object MyCommandOwner : PluginCommandOwner(PixivHelperMain)

    override fun onLoad() {
    }

    override fun onEnable() {
        ShowSetting.register()
        launch {
            PixivHelperStorage.sendMessageToAll("PIXIV助手已上线")
        }
    }

    override fun onDisable() {
        ShowSetting.unregister()
    }
}