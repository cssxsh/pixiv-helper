package xyz.cssxsh.mirai.plugin.tools

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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import kotlin.io.use

object PanUpdater {
    private const val PRE_CREATE = "https://pan.baidu.com/api/precreate"
    private const val SUPER_FILE = "https://c3.pcs.baidu.com/rest/2.0/pcs/superfile2"
    private const val CREATE_FILE = "https://pan.baidu.com/api/create"
    private const val BLOCK_SIZE = 4L * 1024 * 1024
    private const val MAX_ASYNC = 8
    private fun httpClient(): HttpClient = HttpClient(OkHttp) {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }
        BrowserUserAgent()
        ContentEncoding {
            gzip()
            deflate()
            identity()
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 600_000
        }
    }

    private suspend fun HttpClient.preCreate(
        path: String,
        localMtime: Long,
        config: PanConfig,
        md5List: Set<String> = setOf("5910a591dd8fc18c32a8f3df4fdc1761","a5fc157d78e6ad1c7e114b056c92821e")
    ) = post<PreCreateData>(PRE_CREATE) {
        header(HttpHeaders.Origin, "https://pan.baidu.com")
        header(HttpHeaders.Referrer, "https://pan.baidu.com/disk/home?")
        header(HttpHeaders.Cookie, config.cookies)

        parameter("channel", "chunlei")
        parameter("web", 1)
        parameter("app_id", 250528)
        parameter("bdstoken", config.bdsToken)
        parameter("logid", config.logId)
        parameter("clienttype", 0)

        body = FormDataContent(Parameters.build {
            append("path", config.targetPath + "/" + path)
            append("autoinit", 1.toString())
            append("target_path", config.targetPath)
            append("block_list", "[${md5List.joinToString(",") { "\"${it}\"" }}]")
            append("local_mtime", localMtime.toString())
        })
    }

    private suspend fun HttpClient.superFile(
        data: ByteArray,
        index: Int,
        uploadId: String,
        path: String,
        config: PanConfig
    ) = post<String>(SUPER_FILE) {
        header(HttpHeaders.Origin, "https://pan.baidu.com")
        header(HttpHeaders.Referrer, "https://pan.baidu.com/")
        header(HttpHeaders.Cookie, config.cookies)

        parameter("method", "upload")
        parameter("app_id", 250528)
        parameter("channel", "chunlei")
        parameter("clienttype", 0)
        parameter("web", 1)
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
    }.let {
        Json.decodeFromString(SuperFileData.serializer(), it)
    }

    private suspend fun HttpClient.superFileOrNull(
        data: ByteArray,
        index: Int,
        uploadId: String,
        path: String,
        config: PanConfig
    ): SuperFileData = runCatching {
        superFile(data, index, uploadId, path, config)
    }.onFailure {
        if (it !is ClientRequestException) throw it
    }.getOrElse { superFileOrNull(data, index, uploadId, path, config) }

    private suspend fun HttpClient.createFile(
        length: Long,
        uploadId: String,
        path: String,
        localMtime: Long,
        md5List: List<String>,
        config: PanConfig
    ) = post<CreateResultData>(CREATE_FILE) {
        header(HttpHeaders.Origin, "https://pan.baidu.com")
        header(HttpHeaders.Referrer, "https://pan.baidu.com/disk/home?")
        header(HttpHeaders.Cookie, config.cookies)

        parameter("channel", "chunlei")
        parameter("web", 1)
        parameter("rtype", 1)
        parameter("app_id", 250528)
        parameter("bdstoken", config.bdsToken)
        parameter("logid", config.logId)
        parameter("clienttype", 0)

        body = MultiPartFormDataContent(formData {
            append("path", config.targetPath + "/" + path)
            append("size", length)
            append("uploadid", uploadId)
            append("block_list", "[${md5List.joinToString(",") { "\"${it}\"" }}]")
            append("local_mtime", localMtime)
        })
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun CoroutineScope.update(
        sourcePath: String,
        updatePath: String,
        config: PanConfig,
        block: (SuperFileData, Int, Int) -> Unit = { _, _, _ -> }
    ) = launch(Dispatchers.IO) {
        val file = File(sourcePath)
        val length = file.length()
        val localMtime = file.lastModified()
        val blocks = (0 until length step BLOCK_SIZE).map { offset ->
            offset until minOf(offset + BLOCK_SIZE, length)
        }

        httpClient().use { client ->
            val preCreateData = client.preCreate(
                path = updatePath,
                localMtime = localMtime,
                config = config
            )
            val channel = Channel<Int>(MAX_ASYNC)
            var count = 0
            val md5List = blocks.mapIndexed { index, range ->
                async {
                    channel.send(index)
                    synchronized(file) {
                        RandomAccessFile(File(sourcePath), "r").run {
                            seek(range.first)
                            range.map { readByte() }.toByteArray()
                        }
                    }.let { data ->
                        client.superFileOrNull(
                            data = data,
                            index = index,
                            uploadId = preCreateData.uploadId,
                            path = updatePath,
                            config = config
                        ).also {
                            channel.receive()
                            block(it, ++count, blocks.size)
                        }.md5
                    }
                }
            }.awaitAll()
            client.createFile(
                length = length,
                localMtime = localMtime,
                path = updatePath,
                md5List = md5List,
                uploadId = preCreateData.uploadId,
                config = config
            )
        }
    }

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
        val size: Int
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
        val uploadId: String
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
        val uploadId: String
    )
}
