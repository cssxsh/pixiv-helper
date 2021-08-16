package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.client.statement.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.baidu.disk.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.exception.*
import java.io.*
import java.time.*
import kotlin.math.*

typealias Ignore = suspend (Throwable) -> Boolean

private val BAD_IP = listOf("210.140.131.224", "210.140.131.225")

internal val PIXIV_IMAGE_IP: List<String> = (134..147).map { "210.140.92.${it}" }

internal val PIXIV_API_IP: List<String> = (199..229).map { "210.140.131.${it}" } - BAD_IP

internal val PIXIV_SKETCH_IP: List<String> = listOf("210.140.175.130", "210.140.174.37", "210.140.170.179")

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
            val url = throwable.response.request.url
            val request = throwable.response.request.headers.toMap()
            val response = throwable.response.headers.toMap()
            when {
                "Please check your Access Token to fix this." in throwable.message -> {
                    mutex.withLock {
                        if (expires >= OffsetDateTime.now()) {
                            expires = OffsetDateTime.MIN
                            logger.warning { "PIXIV API OAuth 错误, 将刷新 Token $url with $request" }
                        }
                    }
                    true
                }
                "Rate Limit" in throwable.message -> {
                    logger.warning { "PIXIV API限流, 将延时: ${PIXIV_RATE_LIMIT_DELAY}ms $url with $response" }
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
        is MatchContentLengthException -> false
        is HttpRequestTimeoutException -> true
        is IOException
        -> {
            logger.warning { "Pixiv Download 错误, 已忽略: $throwable" }
            delay(++PixivDownloadDelayCount * 1000L)
            PixivDownloadDelayCount--
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
    "*.pximg.net" to PIXIV_IMAGE_IP,
    "*.pixiv.net" to PIXIV_API_IP,
    "sketch.pixiv.net" to PIXIV_SKETCH_IP
)

internal val DEFAULT_PIXIV_CONFIG = PixivConfig(host = DEFAULT_PIXIV_HOST + PIXIV_HOST)

internal fun PixivHelperSettings.init() {
    cacheFolder.mkdirs()
    backupFolder.mkdirs()
    tempFolder.mkdirs()
    profilesFolder.mkdirs()
    articlesFolder.mkdirs()
    logger.info { "CacheFolder: ${cacheFolder.absolutePath}" }
    logger.info { "BackupFolder: ${backupFolder.absolutePath}" }
    logger.info { "TempFolder: ${tempFolder.absolutePath}" }
    PixivHelperPlugin.launch(SupervisorJob()) {
        val count = ArtWorkInfo.count()
        if (count < eroChunk) {
            logger.warning { "缓存数 $count < ${eroChunk}，建议使用指令( /cache recommended )进行缓存" }
        } else {
            logger.info { "缓存数 $count " }
        }
    }
}

internal fun BaiduNetDiskUpdater.init() = PixivHelperPlugin.launch(SupervisorJob()) {
    loadToken()
    runCatching {
        check(appId != 0L) { "网盘未配置 Oauth 信息，如需要不需要上传备份文件功能，请忽略" }
        getUserInfo()
    }.onSuccess {
        logger.info {
            "百度网盘: ${it.baiduName} 已登录"
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
 * https://www.pixiv.net/i/79695391
 * https://www.pixiv.net/artworks/79695391
 * https://www.pixiv.net/member_illust.php?mode=medium&illust_id=82876433
 */
internal val URL_ARTWORK_REGEX = """(?<=pixiv\.net/(i/|artworks/|illust_id=))\d+""".toRegex()

/**
 * https://www.pixiv.net/u/902077
 * https://www.pixiv.net/users/902077
 * http://www.pixiv.net/member.php?id=902077
 */
internal val URL_USER_REGEX = """(?<=pixiv\.net/(u/|users/|member\.php\?id=))\d+""".toRegex()

/**
 * [リダイレクトURLサービス](https://www.pixiv.net/info.php?id=1554)
 *
 * https://pixiv.me/milkpanda-yellow
 */
internal val URL_PIXIV_ME_REGEX = """(?<=pixiv\.me/)[0-9a-z_-]{3,32}""".toRegex()

internal const val PixivMirrorHost = "i.pixiv.cat"

internal val MIN_SIMILARITY = sqrt(5.0).minus(1).div(2)

internal const val ERO_CHUNK = 16

internal const val ERO_UP_EXPIRE = 10 * 1000L

internal const val ERO_BOOKMARKS = 1L shl 12

internal const val ERO_PAGE_COUNT = 3

internal val ERO_TAG_EXCLUDE = """([hH]olo|僕のヒーローアカデミア)""".toRegex()

internal const val LOAD_LIMIT = 5_000L

internal const val TASK_LOAD = PAGE_SIZE * 3

internal const val TAG_TOP_LIMIT = 10

internal val CompletedJob: Job = Job().apply { complete() }