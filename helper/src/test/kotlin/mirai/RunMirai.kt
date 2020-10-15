package mirai

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.util.Identity.decode
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
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths


@ConsoleExperimentalApi
@ConsoleTerminalExperimentalApi
object RunMirai {

    private val attributes = EncodingAttributes().apply {
        setInputFormat("mp3")
        setOutputFormat("amr")
        setAudioAttributes(AudioAttributes().apply {
            setBitRate(64000)
            setChannels(1)
            setSamplingRate(22050)
        })
    }

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
                        """.+爬""".toRegex() matchingReply { result ->
                            File(".").resolve("${result.value}.amr").apply {
                                if (canRead().not()) {
                                    HttpClient(OkHttp).use { client ->
                                        client.get<ByteArray>("https://fanyi.baidu.com/gettts") {
                                            parameter("lan", "zh")
                                            parameter("text", result.value)
                                            parameter("spd", 5)
                                            parameter("source", "web")
                                        }
                                    }.let {
                                        File("tts.mp3").writeBytes(it)
                                    }
                                    Encoder().encode(MultimediaObject(File("tts.mp3")), this, attributes)
                                }
                            }.let {
                                group.uploadVoice(it.inputStream())
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