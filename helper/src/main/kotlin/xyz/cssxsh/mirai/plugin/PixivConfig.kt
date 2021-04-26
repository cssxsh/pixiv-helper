package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.delay
import net.mamoe.mirai.utils.*
import okhttp3.internal.http2.ConnectionShutdownException
import okhttp3.internal.http2.StreamResetException
import org.apache.ibatis.mapping.Environment
import org.apache.ibatis.session.Configuration
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory
import org.sqlite.JDBC
import org.sqlite.SQLiteConfig
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.dao.*
import xyz.cssxsh.pixiv.PixivConfig
import xyz.cssxsh.pixiv.apps.PAGE_SIZE
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.math.sqrt
import kotlin.time.*

typealias Ignore = suspend (Throwable) -> Boolean

private val BAD_IP = listOf("210.140.131.224", "210.140.131.225")

private val PIXIV_IMAGE_IP: List<String> = (134..147).map { "210.140.92.${it}" } - BAD_IP

private val PIXIV_NET_IP: List<String> = (199..229).map { "210.140.131.${it}" } - BAD_IP

internal val PIXIV_RATE_LIMIT_DELAY = (3).minutes

internal val PixivApiIgnore: Ignore = { throwable ->
    when (throwable) {
        is SSLException,
        is EOFException,
        is ConnectException,
        is SocketTimeoutException,
        is HttpRequestTimeoutException,
        is StreamResetException,
        is UnknownHostException,
        is SocketException,
        -> {
            logger.warning { "PIXIV API错误, 已忽略: $throwable" }
            true
        }
        else -> when (throwable.message) {
            "Required SETTINGS preface not received" -> {
                logger.warning { "PIXIV API错误, 已忽略: $throwable" }
                true
            }
            "Rate Limit" -> {
                logger.warning { "PIXIV API限流, 将延时: $PIXIV_RATE_LIMIT_DELAY" }
                delay(PIXIV_RATE_LIMIT_DELAY)
                true
            }
            else -> false
        }
    }
}

internal val PixivDownloadIgnore: Ignore = { throwable ->
    when (throwable) {
        is SSLException,
        is EOFException,
        is SocketException,
        is SocketTimeoutException,
        is HttpRequestTimeoutException,
        is StreamResetException,
        is NullPointerException,
        is UnknownHostException,
        is ConnectionShutdownException,
        -> true
        else -> when {
            throwable.message?.contains("Required SETTINGS preface not received") == true -> true
            throwable.message?.contains("Completed read overflow") == true -> true
            throwable.message?.contains("""Expected \d+, actual \d+""".toRegex()) == true -> true
            throwable.message?.contains("closed") == true -> true
            else -> false
        }
    }
}

internal val SearchApiIgnore: Ignore = { throwable ->
    when (throwable) {
        is SSLException,
        is EOFException,
        is SocketException,
        is SocketTimeoutException,
        is HttpRequestTimeoutException,
        is StreamResetException,
        is NullPointerException,
        is UnknownHostException,
        is ConnectionShutdownException,
        -> {
            logger.warning { "Search Api 错误, 已忽略: ${throwable.message}" }
            true
        }
        else -> false
    }
}

internal val NaviRankApiIgnore: Ignore = { throwable ->
    when (throwable) {
        is SSLException,
        is EOFException,
        is SocketException,
        is SocketTimeoutException,
        is HttpRequestTimeoutException,
        is StreamResetException,
        is NullPointerException,
        is UnknownHostException,
        is ConnectionShutdownException,
        -> {
            logger.warning { "NaviRank API错误, 已忽略: ${throwable.message}" }
            true
        }
        else -> false
    }
}

internal val PIXIV_HOST = mapOf(
    "i.pximg.net" to PIXIV_IMAGE_IP,
    "s.pximg.net" to PIXIV_IMAGE_IP,
    "oauth.secure.pixiv.net" to PIXIV_NET_IP,
    "app-api.pixiv.net" to PIXIV_NET_IP,
    "public-api.secure.pixiv.net" to PIXIV_NET_IP,
    "public.pixiv.net" to PIXIV_NET_IP,
    "www.pixiv.net" to PIXIV_NET_IP,
    "pixiv.me" to PIXIV_NET_IP
)

internal val DEFAULT_PIXIV_CONFIG = PixivConfig(host = PIXIV_HOST)

internal val InitSqlConfiguration = Configuration()

internal fun Configuration.init(file: File) = apply {
    environment = Environment("development", JdbcTransactionFactory(), SQLiteConnectionPoolDataSource().apply {
        config.apply {
            enforceForeignKeys(true)
            setCacheSize(8196)
            setPageSize(8196)
            setJournalMode(SQLiteConfig.JournalMode.MEMORY)
            enableCaseSensitiveLike(true)
            setTempStore(SQLiteConfig.TempStore.MEMORY)
            setSynchronous(SQLiteConfig.SynchronousMode.OFF)
            setEncoding(SQLiteConfig.Encoding.UTF8)
            PixivHelperSettings.sqliteConfig.forEach { (pragma, value) ->
                setPragma(pragma, value)
            }
        }
        url = "${JDBC.PREFIX}${file.absolutePath}"
    })
    addMapper(ArtWorkInfoMapper::class.java)
    addMapper(FileInfoMapper::class.java)
    addMapper(StatisticInfoMapper::class.java)
    addMapper(TagInfoMapper::class.java)
    addMapper(UserInfoMapper::class.java)
}

internal fun PixivHelperSettings.init() {
    cacheFolder.mkdirs()
    backupFolder.mkdirs()
    tempFolder.mkdirs()
    profilesFolder.mkdirs()
    if (sqlite.exists().not()) {
        this::class.java.getResourceAsStream("pixiv.sqlite")?.use {
            sqlite.writeBytes(it.readAllBytes())
        }
    }
    logger.info { "CacheFolder: ${cacheFolder.absolutePath}" }
    logger.info { "BackupFolder: ${backupFolder.absolutePath}" }
    logger.info { "TempFolder: ${tempFolder.absolutePath}" }
    logger.info { "Sqlite: ${sqlite.absolutePath}" }
}

internal const val PixivMirrorHost = "i.pixiv.cat"

internal val MIN_SIMILARITY = (sqrt(5.0) - 1) / 2

internal val SEARCH_EXPIRE = (1).hours

internal const val ERO_INTERVAL = 16

internal val ERO_UP_EXPIRE = (10).seconds

internal const val ERO_BOOKMARKS = 1L shl 12

internal const val ERO_PAGE_COUNT = 3

internal const val LOAD_LIMIT = 5_000L

internal const val TASK_LOAD = PAGE_SIZE * 5

internal const val TAG_TOP_LIMIT = 10L