package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
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
import xyz.cssxsh.mirai.plugin.model.ArtWorkInfo
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.PixivConfig
import xyz.cssxsh.pixiv.SanityLevel
import xyz.cssxsh.pixiv.apps.PAGE_SIZE
import java.io.IOException
import kotlin.math.sqrt

typealias Ignore = suspend (Throwable) -> Boolean

private val BAD_IP = listOf("210.140.131.224", "210.140.131.225")

private val PIXIV_IMAGE_IP: List<String> = (134..147).map { "210.140.92.${it}" } - BAD_IP

private val PIXIV_NET_IP: List<String> = (199..229).map { "210.140.131.${it}" } - BAD_IP

internal const val PIXIV_RATE_LIMIT_DELAY = 3 * 60 * 1000L

internal const val PIXIV_OAUTH_DELAY = 10 * 1000L

internal val PixivApiIgnore: Ignore = { throwable ->
    when (throwable) {
        is IOException,
        is HttpRequestTimeoutException,
        -> {
            // logger.warning { "Pixiv Api 错误, 已忽略: $throwable" }
            logger.warning({ "Pixiv Api 错误, 已忽略: $throwable" }, throwable)
            true
        }
        else -> when (throwable.message) {
            "Error occurred at the OAuth process. Please check your Access Token to fix this." -> {
                logger.warning { "PIXIV API OAuth 错误, 将延时: $PIXIV_OAUTH_DELAY" }
                delay(PIXIV_OAUTH_DELAY)
                true
            }
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

private var PixivDownloadDelayCount = 0

internal val PixivDownloadIgnore: Ignore = { throwable ->
    when (throwable) {
        is HttpRequestTimeoutException,
        is SocketTimeoutException,
        is ConnectTimeoutException -> {
            // logger.warning { "Pixiv Download 错误, 进入延时: $throwable" }
            delay((++PixivDownloadDelayCount) * 1000L)
            PixivDownloadDelayCount--
            true
        }
        is IOException
        -> {
            logger.warning { "Pixiv Download 错误, 已忽略: $throwable" }
            true
        }
        else -> when {
            throwable.message?.contains("Required SETTINGS preface not received") == true -> true
            throwable.message?.contains("Completed read overflow") == true -> true
            throwable.message?.contains("""Expected \d+, actual \d+""".toRegex()) == true -> true
            throwable.message?.contains("closed") == true -> true
            else -> false
        }
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
    "pixiv.me" to PIXIV_NET_IP
)

internal val DEFAULT_PIXIV_CONFIG = PixivConfig(host = PIXIV_HOST)

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
    PixivHelperPlugin.launch {
        val count = useMappers { it.artwork.count() }
        if (count < eroInterval) {
            logger.warning {
                "缓存数量过少，建议使用指令( /cache walkthrough )进行缓存"
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
        logger.warning({ "百度网盘初始化失败" }, it)
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
 * [https://www.pixiv.net/info.php?id=1554]
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