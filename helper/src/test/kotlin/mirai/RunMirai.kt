package mirai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import mirai.TTS.getAmrFile
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.terminal.ConsoleTerminalExperimentalApi
import net.mamoe.mirai.console.terminal.MiraiConsoleImplementationTerminal
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.event.subscribeGroupMessages
import java.nio.file.Path
import java.nio.file.Paths


@ConsoleExperimentalApi
@ConsoleTerminalExperimentalApi
object RunMirai {

    private fun miraiConsoleImpl(rootPath: Path) = MiraiConsoleImplementationTerminal(
        rootPath = rootPath,
        dataStorageForJvmPluginLoader = JsonPluginDataStorage(rootPath.resolve("data"), false),
        dataStorageForBuiltIns = JsonPluginDataStorage(rootPath.resolve("data"), false),
        configStorageForJvmPluginLoader = JsonPluginDataStorage(rootPath.resolve("config"), true),
        configStorageForBuiltIns = JsonPluginDataStorage(rootPath.resolve("config"), true),
    )

    @JvmStatic
    fun main(args: Array<String>) {
        // 默认在 /test 目录下运行
        MiraiConsoleTerminalLoader.parse(args, exitProcess = true)
        MiraiConsoleTerminalLoader.startAsDaemon(miraiConsoleImpl(Paths.get(".").toAbsolutePath()))
        MiraiConsole.subscribeEvent()
        try {
            runBlocking {
                MiraiConsole.job.join()
            }
        } catch (e: CancellationException) {
            // ignored
        }
    }

    private fun MiraiConsole.subscribeEvent() = apply {
        subscribeAlways<NewFriendRequestEvent> {
            accept()
        }
        subscribeAlways<BotInvitedJoinGroupRequestEvent> {
            accept()
        }
        subscribeGroupMessages {
            """(?:说：|say:)(.+)""".toRegex() matchingReply { result ->
                val text = if (result.groupValues[1].length < 128) result.groupValues[1] else "太长不说"
                group.uploadVoice(getAmrFile(text).inputStream())
            }
        }
    }
}