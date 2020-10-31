package mirai.command

import mirai.tools.TTS
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.uploadAsGroupVoice

object TTSCommand : SimpleCommand(
    owner = TempCommandOwner,
    "tts", "say", "说",
    description = "TTS指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    @ConsoleExperimentalApi
    @Handler
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.handle() {
        val text = message.content.replaceFirst("""(tts|say|说)""".toRegex(), "").trim().takeIf { it.length < 256 } ?: "太长不说"
        if (text.isNotEmpty()) reply(TTS.getAmrFile(text).inputStream().uploadAsGroupVoice(fromEvent.group))
    }
}