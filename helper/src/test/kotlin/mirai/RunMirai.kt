package mirai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.terminal.ConsoleTerminalExperimentalApi
import net.mamoe.mirai.console.terminal.MiraiConsoleImplementationTerminal
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.event.subscribeGroupMessages
import java.io.File
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
        try {
            runBlocking {
                MiraiConsole.apply {
                    subscribeAlways<NewFriendRequestEvent> {
                        accept()
                    }
                    subscribeAlways<BotInvitedJoinGroupRequestEvent> {
                        accept()
                    }
                    subscribeGroupMessages {
                        atBot {
                            quoteReply("部分指令需要好友私聊，已添加自动好友验证\n有事请联系：QQ: 1438159989")
                        }
                        File(".").listFiles()!!.filter { file ->
                            ".amr" in  file.name &&  file.canRead()
                        }.forEach { file ->
                            file.name.run {
                                subSequence(0, lastIndexOf("."))
                            }.toString() reply {
                                group.uploadVoice(file.inputStream())
                            }
                        }
                    }
                }.job.join()
            }
        } catch (e: CancellationException) {
            // ignored
        }
    }
}