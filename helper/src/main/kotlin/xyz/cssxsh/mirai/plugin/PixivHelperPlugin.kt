package xyz.cssxsh.mirai.plugin

import com.google.auto.service.AutoService
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.GroupMuteAllEvent
import net.mamoe.mirai.event.events.GroupOperableEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.event.subscribe
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.utils.minutesToMillis
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*


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

    private val subscribeJobs: MutableList<Job> = mutableListOf()

    // /permission permit u* plugin.xyz.cssxsh.mirai.plugin.pixiv-helper:*
    override fun onEnable() {
        // Settings
        PixivHelperSettings.reload()
        // Data
        PixivCacheData.reload()
        PixivConfigData.reload()
        PixivStatisticalData.reload()
        // cmd
        PixivMethodCommand.register()
        PixivEroCommand.register()
        PixivCacheCommand.register()
        PixivSettingCommand.register()
        PixivSearchCommand.register()
        PixivFollowCommand.register()
        PixivTagCommand.register()
        PixivRecallCommand.register()
        PixivAliasCommand.register()

        //
        subscribeJobs.apply {
            add(subscribeAlways<NewFriendRequestEvent> {
                accept()
            })
            add(subscribeAlways<BotInvitedJoinGroupRequestEvent> {
                accept()
            })
            add(subscribeAlways<GroupMuteAllEvent> {
                PixivHelperManager.remove(group)
            })
        }
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
        PixivAliasCommand.unregister()

        subscribeJobs.apply {
            forEach {
                it.cancel()
            }
            clear()
        }
    }
}