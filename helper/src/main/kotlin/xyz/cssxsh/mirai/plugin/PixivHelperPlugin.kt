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
import xyz.cssxsh.mirai.plugin.model.*
import java.util.logging.*

object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription(id = "xyz.cssxsh.mirai.plugin.pixiv-helper", version = "1.8.1") {
        name("pixiv-helper")
        author("cssxsh")

        dependsOn("io.github.gnuf0rce.file-sync", true)
    }
) {

    private fun JvmPlugin.registerPermission(name: String, description: String): Permission {
        return PermissionService.INSTANCE.register(permissionId(name), description, parentPermission)
    }

    override fun PluginComponentStorage.onLoad() {
        /**
         * @see [com.mchange.v2.log.MLogClasses.SLF4J_CNAME]
         */
        System.setProperty("com.mchange.v2.log.MLog", "com.mchange.v2.log.slf4j.Slf4jMLog")
        /**
         * @see [org.jboss.logging.LoggerProviders.LOGGING_PROVIDER_KEY]
         */
        System.setProperty("org.jboss.logging.provider", "slf4j")
        Logger.getLogger(OkHttpClient::class.java.name).level = Level.OFF
    }

    @OptIn(ConsoleExperimentalApi::class)
    override fun onEnable() {
        HelperSqlConfiguration.load()
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