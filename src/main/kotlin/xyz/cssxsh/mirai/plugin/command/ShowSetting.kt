package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import xyz.cssxsh.mirai.plugin.PixivHelperMain
import xyz.cssxsh.mirai.plugin.PixivHelperSettings

/**
 * 用于打印Pixiv助手设置
 */
object ShowSetting: SimpleCommand(
    owner = PixivHelperMain.MyCommandOwner,
    names = arrayOf("setting"),
    description = "显示当前设置"
) {
    /**
     * 群聊响应处理
     */
    private suspend fun MemberCommandSenderOnMessage.handle() {
        val helper = getHelper()
        sendMessage(helper.setting.toString())
    }

    /**
     * 好友私聊响应处理
     */
    private suspend fun FriendCommandSenderOnMessage.handle() {
        val helper = getHelper()
        sendMessage(helper.setting.toString())
    }

    /**
     * 临时聊天响应处理
     */
    private suspend fun TempCommandSenderOnMessage.handle() {
        val helper = getHelper()
        sendMessage(helper.setting.toString())
    }

    /**
     * 控制台响应处理
     */
    private suspend fun ConsoleCommandSender.handle() {
        sendMessage(PixivHelperSettings.proxy)
    }

    @Handler
    @Suppress("unused")
    suspend fun CommandSender.handler() = when(this) {
        is MemberCommandSenderOnMessage -> handle()
        is FriendCommandSenderOnMessage -> handle()
        is TempCommandSenderOnMessage -> handle()
        is ConsoleCommandSender -> handle()
        else -> Unit
    }
}