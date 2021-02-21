package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import org.apache.ibatis.io.Resources
import org.apache.ibatis.mapping.Environment
import org.apache.ibatis.session.SqlSession
import org.apache.ibatis.session.SqlSessionFactory
import org.apache.ibatis.session.SqlSessionFactoryBuilder
import org.sqlite.SQLiteConfig.*
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.sqliteUrl
import xyz.cssxsh.mirai.plugin.tools.PixivZipper
import kotlin.time.minutes

object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription("xyz.cssxsh.mirai.plugin.pixiv-helper", "0.5.0-dev-1") {
        name("pixiv-helper")
        author("cssxsh")
    }
) {

    private val sqlSessionFactory: SqlSessionFactory by lazy {
        Resources.getResourceAsStream("mybatis-config.xml").use {
            SqlSessionFactoryBuilder().build(it)
        }
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
        PixivStatisticalData.reload()
        PixivAliasData.reload()
        PixivSearchData.reload()
        // Command
        PixivMethodCommand.register()
        PixivEroCommand.register()
        PixivCacheCommand.register()
        PixivSettingCommand.register()
        PixivSearchCommand.register()
        PixivFollowCommand.register()
        PixivTagCommand.register()
        PixivIllustratorCommand.register()
        PixivInfoCommand.register()
        PixivGetCommand.register()
        PixivDeleteCommand.register()

        PixivHelperSettings.cacheFolder.mkdirs()
        PixivHelperSettings.backupFolder.mkdirs()

        sqlSessionFactory.init()
        // Listener
        PixivHelperListener.subscribe()
    }

    override fun onDisable() {
        PixivMethodCommand.unregister()
        PixivEroCommand.unregister()
        PixivEroCommand.unregister()
        PixivSettingCommand.unregister()
        PixivSettingCommand.unregister()
        PixivFollowCommand.unregister()
        PixivTagCommand.unregister()
        PixivIllustratorCommand.unregister()
        PixivInfoCommand.unregister()
        PixivGetCommand.unregister()
        PixivDeleteCommand.unregister()

        PixivHelperListener.stop()

        PixivZipper.backupData()
    }
}