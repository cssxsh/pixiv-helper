package mirai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import mirai.command.BiliBiliCommand
import mirai.command.TTSCommand
import mirai.data.AmrFileData
import mirai.data.BilibiliTaskData
import mirai.data.TempPluginDataHolder
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.plugin.jvm.JvmPluginLoader
import net.mamoe.mirai.console.terminal.ConsoleTerminalExperimentalApi
import net.mamoe.mirai.console.terminal.MiraiConsoleImplementationTerminal
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.event.subscribeAlways
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
        MiraiConsole.setCustomize()
        try {
            runBlocking {
                MiraiConsole.job.join()
            }
        } catch (e: CancellationException) {
            // ignored
        }
    }

    private fun MiraiConsole.setCustomize() {
        JvmPluginLoader.dataStorage.load(TempPluginDataHolder, AmrFileData)
        JvmPluginLoader.dataStorage.load(TempPluginDataHolder, BilibiliTaskData)
        subscribeAlways<NewFriendRequestEvent> {
            accept()
        }
        subscribeAlways<BotInvitedJoinGroupRequestEvent> {
            accept()
        }
        TTSCommand.register()
        BiliBiliCommand.onInit()
        BiliBiliCommand.register()
//        runBlocking {
//            Bot.botInstances.flatMap { it.groups }.forEach { group ->
//                group.sendMessage("我上线啦啊啊啊(此为消息为测试机器人上线自动发送消息)")
//            }
//        }
    }
}