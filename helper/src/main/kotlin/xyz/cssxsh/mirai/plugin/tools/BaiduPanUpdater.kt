package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.compression.*
import io.ktor.client.features.cookies.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.RandomAccessFile
import kotlin.io.use

object BaiduPanUpdater {
    private const val PRE_CREATE = "https://pan.baidu.com/api/precreate"
    private const val SUPER_FILE = "https://c3.pcs.baidu.com/rest/2.0/pcs/superfile2"
    private const val CREATE_FILE = "https://pan.baidu.com/api/create"
    private const val INDEX_PAGE = "https://pan.baidu.com/disk/home"
    private const val BLOCK_SIZE = 4L * 1024 * 1024
    private const val MAX_ASYNC = 8
    private val TOKEN_REGEX = """"(?=bdstoken":")[0-9a-f]{32}""".toRegex()
    private val cookiesStorage = AcceptAllCookiesStorage()
    private fun httpClient(): HttpClient = HttpClient(OkHttp) {
        Json {
            serializer = KotlinxSerializer()
            accept(ContentType.Text.Html)
        }
        BrowserUserAgent()
        ContentEncoding {
            gzip()
            deflate()
            identity()
        }
        install(HttpCookies) {
            storage = cookiesStorage
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 10_000
        }
    }

    private lateinit var config: UserConfig

    private suspend fun <T, R> Iterable<T>.asyncMapIndexed(
        transform: suspend (Int, T) -> R
    ): List<R> = coroutineScope {
        val channel = Channel<Int>(MAX_ASYNC)
        mapIndexed { index, value ->
            async {
                channel.send(index)
                transform(index, value).also {
                    channel.receive()
                }
            }
        }.awaitAll()
    }

    @Serializable
    data class HttpCookie(
        @SerialName("domain")
        val domain: String? = null,
        @SerialName("expirationDate")
        val expirationDate: Double? = null,
        @SerialName("hostOnly")
        val hostOnly: Boolean,
        @SerialName("httpOnly")
        val httpOnly: Boolean,
        @SerialName("id")
        val id: Int,
        @SerialName("name")
        val name: String,
        @SerialName("path")
        val path: String? = null,
        @SerialName("sameSite")
        val sameSite: String,
        @SerialName("secure")
        val secure: Boolean,
        @SerialName("session")
        val session: Boolean,
        @SerialName("storeId")
        val storeId: String,
        @SerialName("value")
        val value: String
    )

    @Serializable
    data class UserConfig(
        @SerialName("channel")
        val channel: String = "chunlei",
        @SerialName("web")
        val web: Int = 1,
        @SerialName("log_id")
        val logId: String,
        @SerialName("app_id")
        val appId: Int = 250528,
        @SerialName("client_type")
        val clientType: Int = 0,
        @SerialName("target_path")
        val targetPath: String,
        @SerialName("cookies")
        val cookies: List<HttpCookie>
    )

    @Serializable
    data class PreCreateData(
        @SerialName("block_list")
        val blockList: List<Int> = emptyList(),
        @SerialName("path")
        val path: String? = null,
        @SerialName("errno")
        val errno: Int,
        @SerialName("request_id")
        val requestId: Long,
        @SerialName("return_type")
        val returnType: Int,
        @SerialName("uploadid")
        val uploadId: String,
    )

    @Serializable
    data class CreateResultData(
        @SerialName("category")
        val category: Int,
        @SerialName("ctime")
        val ctime: Int,
        @SerialName("errno")
        val errno: Int,
        @SerialName("fs_id")
        val fsId: Long,
        @SerialName("isdir")
        val isDir: Int,
        @SerialName("md5")
        val md5: String,
        @SerialName("mtime")
        val mtime: Int,
        @SerialName("name")
        val name: String,
        @SerialName("path")
        val path: String,
        @SerialName("server_filename")
        val serverFilename: String? = null,
        @SerialName("size")
        val size: Int,
    )

    @Serializable
    data class SuperFileData(
        @SerialName("md5")
        val md5: String,
        @SerialName("partseq")
        val partSeq: String,
        @SerialName("request_id")
        val requestId: Long,
        @SerialName("uploadid")
        val uploadId: String,
    )

    fun loadPanConfig(new: UserConfig): Unit = runBlocking {
        config = new
        config.cookies.forEach {
            cookiesStorage.addCookie(INDEX_PAGE, Cookie(
                name = it.name,
                value = it.value,
                expires = it.expirationDate?.run { GMTDate(toLong()) },
                path = it.path,
                domain = it.domain,
                secure = it.secure,
                httpOnly = it.httpOnly
            ))
        }
    }

    private suspend fun HttpClient.getToken() = get<String>(INDEX_PAGE).let {
        TOKEN_REGEX.find(it)!!.value
    }

    private suspend fun HttpClient.preCreate(
        path: String,
        localMtime: Long,
        md5List: Set<String> = setOf("5910a591dd8fc18c32a8f3df4fdc1761", "a5fc157d78e6ad1c7e114b056c92821e"),
        token: String,
    ) = post<PreCreateData>(PRE_CREATE) {
        header(HttpHeaders.Origin, "https://pan.baidu.com")
        header(HttpHeaders.Referrer, INDEX_PAGE)

        parameter("channel", config.channel)
        parameter("web", config.web)
        parameter("app_id", config.appId)
        parameter("bdstoken", token)
        parameter("logid", config.logId)
        parameter("clienttype", config.clientType)

        body = FormDataContent(Parameters.build {
            append("path", config.targetPath + "/" + path)
            append("autoinit", 1.toString())
            append("target_path", config.targetPath)
            append("block_list", md5List.toString())
            append("local_mtime", localMtime.toString())
        })
    }

    private suspend fun HttpClient.superFile(
        data: ByteArray,
        index: Int,
        uploadId: String,
        path: String,
    ) = post<SuperFileData>(SUPER_FILE) {
        header(HttpHeaders.Origin, "https://pan.baidu.com")
        header(HttpHeaders.Referrer, "https://pan.baidu.com/")

        parameter("method", "upload")
        parameter("app_id", config.appId)
        parameter("channel", config.channel)
        parameter("clienttype", config.clientType)
        parameter("web", config.web)
        parameter("logid", config.logId)
        parameter("path", config.targetPath + "/" + path)
        parameter("uploadid", uploadId)
        parameter("uploadsign", 0)
        parameter("partseq", index)

        body = MultiPartFormDataContent(formData {
            append(key = "file", filename = "blob", size = data.size.toLong()) {
                writeFully(data)
            }
        })
    }

    private suspend fun HttpClient.superFileIgnoreException(
        data: ByteArray,
        index: Int,
        uploadId: String,
        path: String,
    ): SuperFileData = runCatching {
        superFile(data, index, uploadId, path)
    }.onFailure {
        if (it !is ClientRequestException && isActive) throw it
    }.getOrElse { superFileIgnoreException(data, index, uploadId, path) }

    private suspend fun HttpClient.createFile(
        length: Long,
        uploadId: String,
        path: String,
        localMtime: Long,
        md5List: List<String>,
        token: String,
    ) = post<CreateResultData>(CREATE_FILE) {
        header(HttpHeaders.Origin, "https://pan.baidu.com")
        header(HttpHeaders.Referrer, "https://pan.baidu.com/disk/home?")

        parameter("channel", config.channel)
        parameter("web", config.web)
        parameter("rtype", 1)
        parameter("app_id", config.appId)
        parameter("bdstoken", token)
        parameter("logid", config.logId)
        parameter("clienttype", config.clientType)

        body = MultiPartFormDataContent(formData {
            append("path", config.targetPath + "/" + path)
            append("size", length)
            append("uploadid", uploadId)
            append("block_list", md5List.toString())
            append("local_mtime", localMtime)
        })
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun update(
        file: File,
        updatePath: String,
    ) = withContext(Dispatchers.IO) {
        val blocks = (0 until file.length() step BLOCK_SIZE).map { offset ->
            offset until minOf(offset + BLOCK_SIZE, file.length())
        }
        httpClient().use { client ->
            val token = client.getToken()
            val preCreateData = client.preCreate(
                path = updatePath,
                localMtime = file.lastModified(),
                token = token
            )
            val md5List = blocks.asyncMapIndexed { index, range ->
                synchronized(file) {
                    RandomAccessFile(file, "r").run {
                        seek(range.first)
                        range.map { readByte() }.toByteArray()
                    }
                }.let { data ->
                    client.superFileIgnoreException(
                        data = data,
                        index = index,
                        uploadId = preCreateData.uploadId,
                        path = updatePath
                    ).md5
                }
            }
            client.createFile(
                length = file.length(),
                localMtime = file.lastModified(),
                path = updatePath,
                md5List = md5List,
                uploadId = preCreateData.uploadId,
                token = token
            )
        }
    }
}
