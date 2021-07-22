package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.PluginConfig
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import org.apache.ibatis.session.SqlSession
import org.apache.ibatis.session.SqlSessionFactory
import org.apache.ibatis.session.SqlSessionFactoryBuilder
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.tools.*

object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription("xyz.cssxsh.mirai.plugin.pixiv-helper", "1.0.3") {
        name("pixiv-helper")
        author("cssxsh")
    }
) {

    internal val sqlSessionFactory: SqlSessionFactory by lazy {
        SqlSessionFactoryBuilder().build(InitSqlConfiguration)
    }

    internal fun <T> useSession(block: (SqlSession) -> T) = synchronized(sqlSessionFactory) {
        sqlSessionFactory.openSession(false).use { session ->
            session.let(block).also { session.commit() }
        }
    }

    private fun <T : PluginConfig> T.save() = loader.configStorage.store(this@PixivHelperPlugin, this)

    // /permission permit u* plugin.xyz.cssxsh.mirai.plugin.pixiv-helper:*
    override fun onEnable() {
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
    }
}