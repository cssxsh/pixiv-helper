package xyz.cssxsh.mirai.pixiv

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.extension.*
import net.mamoe.mirai.console.permission.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.event.*
import xyz.cssxsh.mirai.pixiv.command.*
import xyz.cssxsh.mirai.pixiv.data.*

public object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription(id = "xyz.cssxsh.mirai.plugin.pixiv-helper", version = "2.0.0-M6") {
        name("pixiv-helper")
        author("cssxsh")

        dependsOn("xyz.cssxsh.mirai.plugin.mirai-hibernate-plugin", ">= 2.3.3", false)
        dependsOn("xyz.cssxsh.mirai.plugin.mirai-selenium-plugin", true)
        dependsOn("xyz.cssxsh.mirai.plugin.mirai-skia-plugin", true)
    }
) {

    private fun JvmPlugin.registerPermission(name: String, description: String): Permission {
        return PermissionService.INSTANCE.register(permissionId(name), description, parentPermission)
    }

    override fun PluginComponentStorage.onLoad() {
        runAfterStartup {
            PixivScheduler.start()
        }
    }

    override fun onEnable() {
        PixivHelperSettings.reload()
        PixivConfigData.reload()
        PixivGifConfig.reload()
        PixivAuthData.reload()
        PixivTaskData.reload()
        ImageSearchConfig.reload()

        // Command
        for (command in PixivHelperCommand) {
            command.register()
        }

        initConfiguration()

        PixivEventListener.paserPermission = registerPermission("url", "PIXIV URL 解析")
        PixivEventListener.registerTo(globalEventChannel())
    }

    override fun onDisable() {
        for (command in PixivHelperCommand) {
            command.unregister()
        }

        PixivEventListener.cancelAll()

        PixivScheduler.stop()
    }
}