package xyz.cssxsh.mirai.plugin

import com.google.auto.service.AutoService
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.minutesToMillis
import net.mamoe.mirai.utils.secondsToMillis
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivConfigData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings


@AutoService(JvmPlugin::class)
object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription("xyz.cssxsh.mirai.plugin.pixiv-helper", "0.5.0-dev-1") {
        name("pixiv-helper")
        author("cssxsh")
    }
) {

    @ConsoleExperimentalApi
    override val autoSaveIntervalMillis: LongRange
        get() = 3.minutesToMillis..30.minutesToMillis

    // val qqId = 3337342367L // Bot的QQ号，需为Long类型，在结尾处添加大写L
    // val password = "66RKVt^eX&MfE7" // Bot的密码
    // login 3337342367 66RKVt^eX&MfE7
    // /permission permit u* plugin.xyz.cssxsh.mirai.plugin.pixiv-helper:*
    override fun onEnable() {
        // Settings
        PixivHelperSettings.reload()
        // Data
        PixivCacheData.reload()
        PixivConfigData.reload()
        // cmd
        PixivMethodCommand.register()
        PixivEroCommand.register()
        PixivCacheCommand.register()
        PixivSettingCommand.register()
        PixivSearchCommand.register()
        PixivFollowCommand.register()
    }


    override fun onDisable() {
        PixivMethodCommand.unregister()
        PixivEroCommand.unregister()
        PixivEroCommand.unregister()
        PixivSettingCommand.unregister()
        PixivSettingCommand.unregister()
        PixivFollowCommand.unregister()
    }
}