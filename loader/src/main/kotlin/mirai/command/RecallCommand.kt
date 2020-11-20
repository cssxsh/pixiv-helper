package mirai.command

import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.recall
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.verbose
import net.mamoe.mirai.utils.warning

@ConsoleExperimentalApi
object RecallCommand : SimpleCommand(
    owner = TempCommandOwner,
    "recall", "撤回",
    description = "撤回指令"
) {

    private val logger by lazy {
        MiraiConsole.createLogger("tts")
    }

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle() {
        message[QuoteReply]?.run {
            runCatching {
                logger.verbose { "尝试对${source}进行撤回" }
                fromEvent.subject.recall(source)
            }.onSuccess {
                logger.info { "撤回${source}成功" }
            }.onFailure {
                logger.warning({ "撤回${source}失败" }, it)
            }
        }
    }
}