package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.client.statement.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.utils.*
import org.apache.ibatis.mapping.Environment
import org.apache.ibatis.session.Configuration
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory
import org.sqlite.JDBC
import org.sqlite.SQLiteConfig
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import xyz.cssxsh.baidu.disk.getUserInfo
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.dao.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.exception.*
import java.io.IOException
import java.time.OffsetDateTime
import kotlin.math.sqrt

typealias Ignore = suspend (Throwable) -> Boolean

private val BAD_IP = listOf("210.140.131.224", "210.140.131.225")

private val PIXIV_IMAGE_IP: List<String> = (134..147).map { "210.140.92.${it}" } - BAD_IP

private val PIXIV_NET_IP: List<String> = (199..229).map { "210.140.131.${it}" } - BAD_IP

internal const val PIXIV_RATE_LIMIT_DELAY = 3 * 60 * 1000L

internal val PixivApiIgnore: suspend PixivHelper.(Throwable) -> Boolean = { throwable ->
    when (throwable) {
        is IOException,
        is HttpRequestTimeoutException,
        -> {
            logger.warning { "Pixiv Api 错误, 已忽略: $throwable" }
            true
        }
        is AppApiException -> {
            when {
                "Please check your Access Token to fix this." in throwable.message -> {
                    mutex.withLock {
                        if (expires >= OffsetDateTime.now()) {
                            expires = OffsetDateTime.MIN
                            val headers = throwable.response.request.headers.toMap()
                            logger.warning { "PIXIV API OAuth 错误, 将刷新 Token $headers" }
                        }
                    }
                    true
                }
                "Rate Limit" in throwable.message -> {
                    logger.warning { "PIXIV API限流, 将延时: ${PIXIV_RATE_LIMIT_DELAY}ms" }
                    delay(PIXIV_RATE_LIMIT_DELAY)
                    true
                }
                else -> false
            }
        }
        else -> false
    }
}

private var PixivDownloadDelayCount = 0

internal val PixivDownloadIgnore: Ignore = { throwable ->
    when (throwable) {
        is HttpRequestTimeoutException -> true
        is IOException
        -> {
            logger.warning { "Pixiv Download 错误, 已忽略: $throwable" }
            val message = throwable.message.orEmpty()
            when {
                "Not Match ContentLength" in message -> {
                    delay(10 * 1000L)
                }
                else -> {
                    delay(++PixivDownloadDelayCount * 1000L)
                    PixivDownloadDelayCount--
                }
            }
            true
        }
        else -> false
    }
}

internal fun Ignore(name: String): Ignore = { throwable ->
    when (throwable) {
        is IOException,
        is HttpRequestTimeoutException,
        -> {
            logger.warning { "$name Api 错误, 已忽略: ${throwable.message}" }
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
    "pixiv.me" to PIXIV_NET_IP,
    "accounts.pixiv.net" to PIXIV_NET_IP
)

internal val DEFAULT_PIXIV_CONFIG = PixivConfig(host = DEFAULT_PIXIV_HOST + PIXIV_HOST)

internal val InitSqlConfiguration = Configuration()

internal fun Configuration.init() {
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
        url = "${JDBC.PREFIX}${PixivHelperSettings.sqlite.absolutePath}"
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
    articlesFolder.mkdirs()
    if (sqlite.exists().not()) {
        this::class.java.getResourceAsStream("pixiv.sqlite")?.use {
            sqlite.writeBytes(it.readAllBytes())
        }
    }
    PixivHelperPlugin.sqlSessionFactory.configuration.init()
    logger.info { "CacheFolder: ${cacheFolder.absolutePath}" }
    logger.info { "BackupFolder: ${backupFolder.absolutePath}" }
    logger.info { "TempFolder: ${tempFolder.absolutePath}" }
    logger.info { "Sqlite: ${sqlite.absolutePath}" }
    PixivHelperPlugin.launch(SupervisorJob()) {
        val count = useMappers { it.artwork.count() }
        if (count < eroInterval) {
            logger.warning {
                "缓存数量过少，建议使用指令( /cache recommended )进行缓存"
            }
        }
    }
}

internal fun BaiduNetDiskUpdater.init() = PixivHelperPlugin.launch(SupervisorJob()) {
    loadToken()
    runCatching {
        getUserInfo()
    }.onSuccess {
        logger.info {
            "百度网盘: ${it.baiduName} 已登录, 过期时间 $expires"
        }
    }.onFailure {
        if ("Invalid Bduss" in it.message.orEmpty()) {
            logger.warning { "百度网盘初始化失败, 需要重新登录, $it" }
            return@onFailure
        }
        logger.warning { "百度网盘初始化失败, $it" }
    }
}

/**
 * https://www.pixiv.net/artworks/79695391
 * https://www.pixiv.net/member_illust.php?mode=medium&illust_id=82876433
 */
internal val URL_ARTWORK_REGEX = """(?<=(artworks/|illust_id=))\d+""".toRegex()

/**
 * https://www.pixiv.net/users/902077
 * http://www.pixiv.net/member.php?id=902077
 */
internal val URL_USER_REGEX = """(?<=(users/|member\.php\?id=))\d+""".toRegex()

/**
 * [リダイレクトURLサービス](https://www.pixiv.net/info.php?id=1554)
 *
 * https://pixiv.me/milkpanda-yellow
 */
internal val URL_PIXIV_ME_REGEX = """(?<=pixiv\.me/)[0-9a-z_-]{3,32}""".toRegex()

internal const val PixivMirrorHost = "i.pixiv.cat"

internal val MIN_SIMILARITY = (sqrt(5.0) - 1) / 2

internal const val ERO_INTERVAL = 16

internal const val ERO_UP_EXPIRE = 10 * 1000L

internal const val ERO_BOOKMARKS = 1L shl 12

internal const val ERO_PAGE_COUNT = 3

internal const val ERO_TAG_EXCLUDE = "(.*holo.*|僕のヒーローアカデミア)"

internal const val LOAD_LIMIT = 5_000L

internal const val TASK_LOAD = PAGE_SIZE * 3

internal const val TAG_TOP_LIMIT = 10L

internal val CancelledJob: Job = Job().apply { cancel() }

internal val EmptyArtWorkInfo by lazy {
    ArtWorkInfo(
        pid = 0,
        uid = 0,
        title = "",
        caption = "",
        createAt = 0,
        pageCount = 0,
        sanityLevel = SanityLevel.NONE.ordinal,
        type = 0,
        width = 0,
        height = 0,
        totalBookmarks = 0,
        totalComments = 0,
        totalView = 0,
        age = 0,
        isEro = false,
        deleted = false
    )
}