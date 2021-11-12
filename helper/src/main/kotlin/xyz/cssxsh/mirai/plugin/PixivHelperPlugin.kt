package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.extension.*
import net.mamoe.mirai.console.permission.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScope
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScopeContext
import net.mamoe.mirai.event.*
import okhttp3.*
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.tools.*
import java.util.logging.*

object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription("xyz.cssxsh.mirai.plugin.pixiv-helper", "1.6.6") {
        name("pixiv-helper")
        author("cssxsh")
    }
) {

    @OptIn(ConsoleExperimentalApi::class)
    private fun <T : PluginConfig> T.save() = loader.configStorage.store(this@PixivHelperPlugin, this)

    private fun AbstractJvmPlugin.registerPermission(name: String, description: String): Permission {
        return PermissionService.INSTANCE.register(permissionId(name), description, parentPermission)
    }

    override fun PluginComponentStorage.onLoad() {
        System.setProperty("org.jboss.logging.provider", "slf4j")
        Logger.getLogger("org.hibernate").level = Level.INFO
        Logger.getLogger(OkHttpClient::class.java.name).level = Level.OFF
        org.slf4j.MDC.getMDCAdapter()
    }

    @OptIn(ConsoleExperimentalApi::class)
    override fun onEnable() {
        HelperSqlConfiguration.load(configFolder)
        for (config in PixivHelperConfig) {
            config.reload()
            if (config is ReadOnlyPluginConfig) config.save()
        }
        // Command
        for (command in PixivHelperCommand) {
            command.register()
        }

        PixivHelperSettings.init(childScope())

        PixivHelperListener.subscribe(globalEventChannel(), registerPermission("url", "PIXIV URL 解析"))

        PixivHelperScheduler.start(childScopeContext("PixivHelperScheduler"))

        BaiduNetDiskUpdater.init(childScope())

        ImageSearchConfig.init()

        PixivGifConfig.init()
    }

    override fun onDisable() {
        for (command in PixivHelperCommand) {
            command.unregister()
        }

        PixivHelperListener.stop()

        PixivHelperScheduler.stop()
    }
}