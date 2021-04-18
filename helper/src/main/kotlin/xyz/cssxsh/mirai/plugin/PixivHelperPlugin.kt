package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import org.apache.ibatis.session.SqlSession
import org.apache.ibatis.session.SqlSessionFactory
import org.apache.ibatis.session.SqlSessionFactoryBuilder
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.tools.*
import kotlin.time.*

object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription("xyz.cssxsh.mirai.plugin.pixiv-helper", "0.5.0-dev-1") {
        name("pixiv-helper")
        author("cssxsh")
    }
) {

    private val sqlSessionFactory: SqlSessionFactory by lazy {
        SqlSessionFactoryBuilder().build(InitSqlConfiguration)
    }

    internal fun <T> useSession(block: (SqlSession) -> T) = synchronized(sqlSessionFactory) {
        sqlSessionFactory.openSession(false).use { session ->
            session.let(block).also { session.commit() }
        }
    }

    @ConsoleExperimentalApi
    override val autoSaveIntervalMillis: LongRange
        get() = (10).minutes.toLongMilliseconds()..(30).minutes.toLongMilliseconds()

    // /permission permit u* plugin.xyz.cssxsh.mirai.plugin.pixiv-helper:*
    override fun onEnable() {
        // Settings
        PixivHelperSettings.reload()
        // Data
        PixivConfigData.reload()
        PixivAliasData.reload()
        PixivSearchData.reload()
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
        PixivTaskCommand.register()

        PixivHelperSettings.init()

        sqlSessionFactory.configuration.init(PixivHelperSettings.sqlite)
        // Listener
        PixivHelperListener.subscribe()

        PixivHelperScheduler.start()

        BaiduNetDiskUpdater.loadToken()
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
        PixivTaskCommand.unregister()

        PixivHelperListener.stop()

        PixivHelperScheduler.stop()
    }
}