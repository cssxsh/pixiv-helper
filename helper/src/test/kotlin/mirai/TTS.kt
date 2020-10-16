package mirai

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import java.io.File

@ConsoleExperimentalApi
object TTS {

    private suspend fun <T> useHttpClient(block: suspend (HttpClient) -> T): T = HttpClient(OkHttp) {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }
        BrowserUserAgent()
        ContentEncoding {
            gzip()
            deflate()
            identity()
        }
    }.use {
        block(it)
    }


    private val logger by lazy {
        MiraiConsole.createLogger("TTS")
    }

    @Serializable
    data class FileAlias(
        val files: Map<String, String>
    )

    private val rootPath = File("amr")

    private var fileMap: FileAlias
        get() = Json.decodeFromString(FileAlias.serializer(), File(rootPath, "map.json").apply {
            if (exists().not()) {
                createNewFile()
                writeText(Json.encodeToString(FileAlias.serializer(), FileAlias(files = emptyMap())))
            }
        }.readText())
        set(value) {
            File(rootPath, "map.json").writeText(Json.encodeToString(FileAlias.serializer(), value))
        }

    private suspend fun getAmr(text: String): String = useHttpClient { client ->
        logger.verbose("开始tts $text")
        val file = client.get<ByteArray>("https://fanyi.baidu.com/gettts") {
            parameter("lan", "zh")
            parameter("text", text)
            parameter("spd", 5)
            parameter("source", "web")
        }
        File("tts.mp3").writeBytes(file)
        logger.verbose("开始mp3(${file.size}) -> amr")
        val json = client.submitFormWithBinaryData<String>(
            url = "https://s19.aconvert.com/convert/convert-batch.php",
            formData = formData {
                append(key = "file", filename = "blob", size = file.size.toLong(), contentType = ContentType.Audio.MPEG) {
                    writeFully(file)
                }
                append(key = "targetformat", value = "amr")
            }
        )
        logger.verbose("转换结果: $json")
        val filename = Json.parseToJsonElement(json).jsonObject["filename"]!!.jsonPrimitive.content
        client.get<ByteArray>("https://s19.aconvert.com/convert/p3r68-cdx67/${filename}").let {
            File(rootPath, filename).writeBytes(it)
        }
        fileMap = fileMap.run {
            copy(files = files + (text to filename))
        }
        filename
    }

    suspend fun getAmrFile(text: String): File = File(rootPath, fileMap.files[text] ?: getAmr(text))
}
