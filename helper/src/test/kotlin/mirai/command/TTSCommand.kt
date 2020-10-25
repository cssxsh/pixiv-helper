package mirai.command

import mirai.tools.TTS
import net.mamoe.mirai.console.command.CommandOwner
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionId
import net.mamoe.mirai.console.permission.RootPermission
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.uploadAsGroupVoice

object TTSCommand : SimpleCommand(
    owner = TTSCommandOwner,
    "tts", "say", "说",
    description = "TTS指令",
    prefixOptional = true
) {

    private object TTSCommandOwner : CommandOwner {
        override val parentPermission: Permission
            get() = RootPermission

        override fun permissionId(name: String): PermissionId =
            PermissionId("tts", name)
    }

    @ConsoleExperimentalApi
    @Handler
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.handle() {
        val text = message.content.replaceFirst("""(tts|say|说)""".toRegex(), "").trim().takeIf { it.length < 256 } ?: "太长不说"
        if (text.isNotEmpty()) reply(TTS.getAmrFile(text).inputStream().uploadAsGroupVoice(fromEvent.group))
    }
}