package mirai

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import kotlin.io.use


@ConsoleExperimentalApi
@ConsoleTerminalExperimentalApi
object RunMirai {


    private val logger by lazy {
        MiraiConsole.createLogger("RunMirai")
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
            atBot {
                quoteReply("部分指令需要好友私聊，已添加自动好友验证\n有事请联系：QQ: 1438159989")
            }
            """.+爬""".toRegex() matchingReply { result ->
                File(".").resolve("${result.value}.amr").apply {
                    if (canRead().not()) {
                        HttpClient(OkHttp).use { client ->
                            logger.verbose("开始tts")
                            val file = client.get<ByteArray>("https://fanyi.baidu.com/gettts") {
                                parameter("lan", "zh")
                                parameter("text", result.value)
                                parameter("spd", 5)
                                parameter("source", "web")
                            }
                            logger.verbose("开始mp3 -> amr")
                            val text = client.post<String>("https://s19.aconvert.com/convert/convert-batch.php") {
                                body = MultiPartFormDataContent(formData {
                                    append(key = "file", filename = "tts.mp3", contentType = ContentType.Audio.MPEG) {
                                        writeFully(file)
                                    }
                                    append(key = "targetformat", value = "amr")
                                    append(key = "audiobitratetype", value = 0)
                                    append(key = "customaudiobitrate", value = "")
                                    append(key = "audiosamplingtype", value = 0)
                                    append(key = "customaudiosampling", value = "")
                                    append(key = "code", value = 82000)
                                    append(key = "filelocation", value = "local")
                                })
                            }
                            logger.verbose("转换结果: $text")
                            val filename = Json.parseToJsonElement(text).jsonObject["filename"]!!.jsonPrimitive.content
                            client.get<ByteArray>("https://s19.aconvert.com/convert/p3r68-cdx67/${filename}")
                        }.let {
                            writeBytes(it)
                        }
                    }
                }.let {
                    group.uploadVoice(it.inputStream())
                }
            }
        }
    }
}