package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.permission.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScope
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScopeContext
import net.mamoe.mirai.event.*
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*

@MiraiHibernate(loader = PixivHibernateLoader::class)
object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription(id = "xyz.cssxsh.mirai.plugin.pixiv-helper", version = "1.9.0-RC") {
        name("pixiv-helper")
        author("cssxsh")

        dependsOn("io.github.gnuf0rce.file-sync", true)
        dependsOn("xyz.cssxsh.mirai.plugin.mirai-hibernate-plugin", false)
    }
) {

    private fun JvmPlugin.registerPermission(name: String, description: String): Permission {
        return PermissionService.INSTANCE.register(permissionId(name), description, parentPermission)
    }

    @OptIn(ConsoleExperimentalApi::class)
    override fun onEnable() {
        for (config in PixivHelperConfig) {
            config.reload()
            if (config is ReadOnlyPluginConfig) config.save()
        }
        // Command
        for (command in PixivHelperCommand) {
            command.register()
        }

        initConfiguration(childScope())

        PixivHelperListener.subscribe(globalEventChannel(), registerPermission("url", "PIXIV URL 解析"))

        PixivHelperScheduler.start(childScopeContext("PixivHelperScheduler"))
    }

    override fun onDisable() {
        for (command in PixivHelperCommand) {
            command.unregister()
        }

        PixivHelperListener.stop()

        PixivHelperScheduler.stop()
    }
}