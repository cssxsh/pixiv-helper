package mirai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.terminal.ConsoleTerminalExperimentalApi
import net.mamoe.mirai.console.terminal.MiraiConsoleImplementationTerminal
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.containsFriend
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import java.nio.file.Paths

@ConsoleExperimentalApi
@ConsoleTerminalExperimentalApi
object RunMirai {

    private val rootPath = Paths.get(".").toAbsolutePath()

    private fun miraiConsoleImpl() = MiraiConsoleImplementationTerminal(
        rootPath = rootPath,
        dataStorageForJvmPluginLoader = JsonPluginDataStorage(rootPath.resolve("data"), false),
        dataStorageForBuiltIns = JsonPluginDataStorage(rootPath.resolve("data"), false),
        configStorageForJvmPluginLoader = JsonPluginDataStorage(rootPath.resolve("config"), true),
        configStorageForBuiltIns = JsonPluginDataStorage(rootPath.resolve("config"), true),
    )

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        // 默认在 /test 目录下运行
        MiraiConsoleTerminalLoader.parse(args = args, exitProcess = true)
        MiraiConsoleTerminalLoader.startAsDaemon(miraiConsoleImpl())
        MiraiConsole.setCustomize()
        try {
            MiraiConsole.job.join()
        } catch (e: CancellationException) {
            // ignored
        }
    }

    private fun MiraiConsole.setCustomize(): Unit = GlobalEventChannel.parentScope(this).run {
        subscribeAlways<NewFriendRequestEvent> {
            accept()
        }
        subscribeAlways<BotInvitedJoinGroupRequestEvent> {
            if (bot.containsFriend(invitorId)) {
                accept()
            } else {
                ignore()
            }
        }
    }
}