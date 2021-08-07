package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.plugin.jvm.*
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.tools.*
import java.util.logging.*

object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription("xyz.cssxsh.mirai.plugin.pixiv-helper", "1.2.0") {
        name("pixiv-helper")
        author("cssxsh")
    }
) {

    private fun <T : PluginConfig> T.save() = loader.configStorage.store(this@PixivHelperPlugin, this)

    override fun onEnable() {
        Logger.getLogger("org.hibernate").level = Level.WARNING
        HelperSqlConfiguration.load(configFolder)
        // Settings
        PixivHelperSettings.reload()
        PixivHelperSettings.save()
        NetdiskOauthConfig.reload()
        NetdiskOauthConfig.save()
        ImageSearchConfig.reload()
        ImageSearchConfig.save()
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

        PixivHelperListener.subscribe()

        PixivHelperScheduler.start()

        BaiduNetDiskUpdater.init()
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

        useSession { it.flush() }
    }
}