package mirai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.MiraiConsole
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
        override fun run(): Unit = runBlocking {
            MiraiConsole.mainLogger.info("Stopping mirai-console")
            runCatching {
                MiraiConsole.job.cancelAndJoin()
            }.onSuccess {
                MiraiConsole.mainLogger.info("mirai-console stopped successfully.")
            }.onFailure {
                if (it is CancellationException) {
                    MiraiConsole.mainLogger.info("mirai-console stopped successfully.")
                } else {
                    MiraiConsole.mainLogger.error("Exception in stop", it)
                }
            }
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