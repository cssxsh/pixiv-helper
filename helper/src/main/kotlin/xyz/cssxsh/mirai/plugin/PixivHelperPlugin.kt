package xyz.cssxsh.mirai.plugin

import com.google.auto.service.AutoService
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.hoursToMillis
import net.mamoe.mirai.utils.minutesToMillis
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.tools.Zipper

@AutoService(JvmPlugin::class)
object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription("xyz.cssxsh.mirai.plugin.pixiv-helper", "0.5.0-dev-1") {
        name("pixiv-helper")
        author("cssxsh")
    }
) {

    @ConsoleExperimentalApi
    override val autoSaveIntervalMillis: LongRange
        get() = 3.minutesToMillis..30.hoursToMillis

    private val listener = PixivHelperListener(this.coroutineContext)

    // /permission permit u* plugin.xyz.cssxsh.mirai.plugin.pixiv-helper:*
    override fun onEnable() {
        // Settings
        PixivHelperSettings.reload()
        // Data
        PixivCacheData.reload()
        PixivConfigData.reload()
        PixivStatisticalData.reload()
        PixivAliasData.reload()
        // Command
        PixivMethodCommand.register()
        PixivEroCommand.register()
        PixivCacheCommand.register()
        PixivSettingCommand.register()
        PixivSearchCommand.register()
        PixivFollowCommand.register()
        PixivTagCommand.register()
        PixivRecallCommand.register()
        PixivIllustratorCommand.register()
        PixivInfoCommand.register()
        PixivGetCommand.register()

        // Listener
        listener.listen()
    }


    override fun onDisable() {
        PixivMethodCommand.unregister()
        PixivEroCommand.unregister()
        PixivEroCommand.unregister()
        PixivSettingCommand.unregister()
        PixivSettingCommand.unregister()
        PixivFollowCommand.unregister()
        PixivTagCommand.unregister()
        PixivRecallCommand.unregister()
        PixivIllustratorCommand.unregister()
        PixivInfoCommand.unregister()
        PixivGetCommand.unregister()

        listener.stop()
        runBlocking {
            Zipper.backupAsync().await()
        }
    }
}