package xyz.cssxsh.mirai.plugin

import com.google.auto.service.AutoService
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivConfigData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import java.io.File


@AutoService(JvmPlugin::class)
object PixivHelperPlugin : KotlinPlugin(
    JvmPluginDescription("xyz.cssxsh.mirai.plugin.pixiv-helper", "0.5.0-dev-1") {
        author("cssxsh")
    }
) {

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
        PixivMethod.register()
        PixivEro.register()
        PixivCache.register()
        PixivConfig.register()
        PixivSearch.register()
    }


    override fun onDisable() {
        PixivMethod.unregister()
        PixivEro.unregister()
        PixivEro.unregister()
        PixivConfig.unregister()
        PixivConfig.unregister()
        PixivHelperManager.closeAll()
    }

    /**
     * 缓存目录
     */
    val cacheFolder: File by lazy {
        if (PixivHelperSettings.cachePath.isEmpty()) {
            File(dataFolder, "cache").apply { mkdir() }
        } else {
            File(PixivHelperSettings.cachePath).apply { mkdir() }
        }
    }

    /**
     * 图片目录
     */
    fun imagesFolder(pid: Long): File = File(cacheFolder, pid.toString()).apply { mkdir() }
}