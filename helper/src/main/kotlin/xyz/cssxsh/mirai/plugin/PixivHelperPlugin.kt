package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.permission.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScopeContext
import net.mamoe.mirai.event.*
import okhttp3.*
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.tools.*
import java.util.logging.*

object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription("xyz.cssxsh.mirai.plugin.pixiv-helper", "1.5.1") {
        name("pixiv-helper")
        author("cssxsh")
    }
) {

    private fun <T : PluginConfig> T.save() = loader.configStorage.store(this@PixivHelperPlugin, this)

    private fun AbstractJvmPlugin.registerPermission(name: String, description: String): Permission {
        return PermissionService.INSTANCE.register(permissionId(name), description, parentPermission)
    }

    init {
        System.setProperty("org.jboss.logging.provider", "slf4j")
        Logger.getLogger("org.hibernate").level = Level.INFO
        Logger.getLogger(OkHttpClient::class.java.name).level = Level.OFF
        org.slf4j.MDC.getMDCAdapter()
    }

    override fun onEnable() {
        HelperSqlConfiguration.load(configFolder)
        // Settings
        PixivHelperSettings.reload()
        PixivHelperSettings.save()
        NetdiskOauthConfig.reload()
        NetdiskOauthConfig.save()
        ImageSearchConfig.reload()
        ImageSearchConfig.save()
        PixivGifConfig.reload()
        PixivGifConfig.save()
        // Data
        PixivConfigData.reload()
        PixivTaskData.reload()
        // Command
        PixivBackupCommand.register()
        PixivCacheCommand.register()
        PixivDeleteCommand.register()
        PixivEroCommand.register()
        PixivFollowCommand.register()
        PixivGetCommand.register()
        PixivIllustratorCommand.register()
        PixivInfoCommand.register()
        PixivMethodCommand.register()
        PixivSearchCommand.register()
        PixivSettingCommand.register()
        PixivTagCommand.register()
        PixivRankCommand.register()
        PixivArticleCommand.register()
        PixivPlayCommand.register()
        PixivTaskCommand.register()
        PixivMarkCommand.register()

        PixivHelperSettings.init()

        PixivHelperListener.subscribe(globalEventChannel(), registerPermission("url", "PIXIV URL 解析"))

        PixivHelperScheduler.start(childScopeContext("PixivHelperScheduler"))

        BaiduNetDiskUpdater.init()

        ImageSearchConfig.init()
    }

    override fun onDisable() {
        PixivBackupCommand.unregister()
        PixivCacheCommand.unregister()
        PixivDeleteCommand.unregister()
        PixivEroCommand.unregister()
        PixivFollowCommand.unregister()
        PixivGetCommand.unregister()
        PixivIllustratorCommand.unregister()
        PixivInfoCommand.unregister()
        PixivMethodCommand.unregister()
        PixivSearchCommand.unregister()
        PixivSettingCommand.unregister()
        PixivTagCommand.unregister()
        PixivRankCommand.unregister()
        PixivArticleCommand.unregister()
        PixivPlayCommand.unregister()
        PixivTaskCommand.unregister()
        PixivMarkCommand.unregister()

        PixivHelperListener.stop()

        PixivHelperScheduler.stop()
    }
}