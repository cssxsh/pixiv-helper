package xyz.cssxsh.mirai.plugin

import com.google.auto.service.AutoService
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*
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
        PixivHelperPluginData.reload()
        PixivHelperSettings.reload()
        PixivCacheData.reload()
        PixivMethod.register()
        PixivEro.register()
        PixivCache.register()
    }

    override fun onDisable() {
        PixivMethod.unregister()
        PixivEro.unregister()
        PixivEro.unregister()
        PixivHelperManager.closeAll()
    }

    /**
     * 缓存目录
     */
    private val cacheFolder: File by lazy { File(dataFolder, "cache").apply { mkdir() } }

    /**
     * 图片目录
     */
    fun imagesFolder(pid: Long): File = File(cacheFolder, pid.toString())
}