package mirai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.command.BuiltInCommands
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.terminal.ConsoleTerminalExperimentalApi
import net.mamoe.mirai.console.terminal.MiraiConsoleImplementationTerminal
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import java.nio.file.Path
import java.nio.file.Paths


@ConsoleExperimentalApi
@ConsoleTerminalExperimentalApi
object RunMirai {

    private fun miraiConsoleImpl(rootPath: Path) = MiraiConsoleImplementationTerminal(
        rootPath = rootPath,
        dataStorageForJvmPluginLoader = JsonPluginDataStorage(rootPath.resolve("data")),
        dataStorageForBuiltIns = JsonPluginDataStorage(rootPath.resolve("data")),
        configStorageForJvmPluginLoader = JsonPluginDataStorage(rootPath.resolve("config")),
        configStorageForBuiltIns = JsonPluginDataStorage(rootPath.resolve("config")),
    )

    private val shutdownThread: Thread = object : Thread() {
        override fun run() = runBlocking {
            BuiltInCommands.StopCommand.run { ConsoleCommandSender.handle() }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // 默认在 /test 目录下运行
        MiraiConsoleTerminalLoader.parse(args, exitProcess = true)
        MiraiConsoleTerminalLoader.startAsDaemon(miraiConsoleImpl(Paths.get(".").toAbsolutePath()))
        Runtime.getRuntime().addShutdownHook(shutdownThread)
        try {
            runBlocking {
                MiraiConsole.job.join()
            }
        } catch (e: CancellationException) {
            // ignored
        }
    }
}