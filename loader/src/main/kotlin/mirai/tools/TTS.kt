package mirai.tools

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.compression.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import mirai.data.AmrFileData
import mirai.data.TTSConvertResult
import mirai.data.TTSLanguageSelect
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.verbose
import java.io.File

@ConsoleExperimentalApi
object TTS {

    private suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = HttpClient(OkHttp) {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
            accept(ContentType.Text.Html)
        }
        BrowserUserAgent()
        ContentEncoding {
            gzip()
            deflate()
            identity()
        }
    }.use { block(it) }

    private val logger by lazy {
        MiraiConsole.createLogger("tts")
    }

    private val rootPath = File("amrs").apply { mkdir() }

    private const val GET_TTS = "https://fanyi.baidu.com/gettts"

    private const val LANG_SELECT = "https://fanyi.baidu.com/langdetect"

    private const val CONVERT_BATCH = "https://s19.aconvert.com/convert/convert-batch.php"

    private const val CONVERT_RESULT = "https://s19.aconvert.com/convert/p3r68-cdx67/"

    private suspend fun getAmr(text: String, speed: Int = 5): String = useHttpClient { client ->
        val language = client.get<TTSLanguageSelect>(LANG_SELECT) {
            parameter("query", text)
        }.takeIf { it.message == "success" }?.language ?: "zh"

        logger.verbose { "开始tts, language: $language, test: '$text'" }
        val file = client.get<ByteArray>(GET_TTS) {
            parameter("lan", language)
            parameter("text", text)
            parameter("spd", speed)
            parameter("source", "web")
        }

        logger.verbose { "开始mp3(${text}, ${file.size}) -> amr" }
        val result = client.submitFormWithBinaryData<TTSConvertResult>(
            url = CONVERT_BATCH,
            formData = formData {
                append(
                    key = "file",
                    filename = "blob",
                    size = file.size.toLong(),
                    contentType = ContentType.Audio.MPEG
                ) {
                    writeFully(file)
                }
                append(key = "targetformat", value = "amr")
            }
        )

        logger.verbose { "转换结果: $result" }
        client.get<ByteArray>(CONVERT_RESULT + result.filename).let {
            File(rootPath, result.filename).writeBytes(it)
        }
        result.filename
    }

    suspend fun getAmrFile(text: String): File = File(rootPath, AmrFileData.files.getOrPut(text) { getAmr(text) })
}
