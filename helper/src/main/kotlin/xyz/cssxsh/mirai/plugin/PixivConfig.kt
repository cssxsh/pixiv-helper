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
import xyz.cssxsh.pixiv.tool.*
import java.io.*
import java.time.*
import kotlin.math.*

typealias Ignore = suspend (Throwable) -> Boolean

internal val PIXIV_IMAGE_SOFTBANK = (134..147).map { "210.140.92.${it}" }

internal val PIXIV_API_SOFTBANK = ((199..223) + (224..229)).map { "210.140.131.${it}" }

internal val PIXIV_SKETCH_SOFTBANK = listOf("210.140.175.130", "210.140.174.37", "210.140.170.179")

internal val SAUCENAO_ORIGIN = listOf("45.32.0.237", "chr1.saucenao.com")

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
    "*.pximg.net" to PIXIV_IMAGE_SOFTBANK,
    "*.pixiv.net" to PIXIV_API_SOFTBANK,
    "sketch.pixiv.net" to PIXIV_SKETCH_SOFTBANK,
    "*.saucenao.com" to SAUCENAO_ORIGIN
)

internal val DEFAULT_PIXIV_CONFIG = PixivConfig(host = DEFAULT_PIXIV_HOST + PIXIV_HOST)

@OptIn(DelicateCoroutinesApi::class)
internal fun PixivHelperSettings.init(scope:  CoroutineScope = GlobalScope) {
    cacheFolder.mkdirs()
    backupFolder.mkdirs()
    tempFolder.mkdirs()
    profilesFolder.mkdirs()
    articlesFolder.mkdirs()
    ugoiraImagesFolder.mkdirs()
    logger.info { "CacheFolder: ${cacheFolder.absolutePath}" }
    logger.info { "BackupFolder: ${backupFolder.absolutePath}" }
    logger.info { "TempFolder: ${tempFolder.absolutePath}" }
    if (pximg.isNotBlank()) {
        logger.warning { "镜像代理已开启 i.pximg.net -> $pximg 不推荐修改这个配置，建议保持留空" }
    }
    if (proxyApi.isNotBlank()) {
        logger.warning { "已加载 API 代理 $proxyApi API代理可能会导致SSL连接异常，请十分谨慎的开启这个功能" }
    }
    if (proxyDownload.isNotBlank()) {
        logger.warning { "已加载 DOWNLOAD 代理 $proxyDownload  图片下载器会对代理产生很大的负荷，请十分谨慎的开启这个功能" }
    }
    if (blockSize <= 0) {
        logger.warning { "分块下载关闭，通常来说分块下载可以加快下载速度，建议开启，但分块不宜太小" }
    } else if (blockSize < HTTP_KILO) {
        logger.warning { "下载分块过小" }
    }
    scope.launch {
        val count = ArtWorkInfo.count()
        if (count < eroChunk) {
            logger.warning { "缓存数 $count < ${eroChunk}，建议使用指令( /cache recommended )进行缓存" }
        } else {
            logger.info { "缓存数 $count " }
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
internal fun BaiduNetDiskUpdater.init(scope:  CoroutineScope = GlobalScope) = scope.launch {
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

internal fun ImageSearchConfig.init() {
    ImageSearcher.key = key
}

internal fun PixivGifConfig.init() {
    if (quantizer !in QUANTIZER_LIST) {
        logger.warning { "PixivGifConfig.quantizer 非原生" }
    } else {
        if ("com.squareup.gifencoder.OctTreeQuantizer" != quantizer) {
            logger.info { "目前GIF合成只有靠CPU算力，推荐使用 OctTreeQuantizer " }
        } else if ("xyz.cssxsh.pixiv.tool.OpenCVQuantizer" == quantizer) {
            System.setProperty(OpenCVQuantizer.MAX_COUNT, "$maxCount")
        }
    }
    if (ditherer !in DITHERER_LIST) {
        logger.warning { "PixivGifConfig.ditherer 非原生" }
    }
}

/**
 * https://www.pixiv.net/i/79695391
 * https://www.pixiv.net/artworks/79695391
 * https://www.pixiv.net/en/artworks/79695391
 * https://www.pixiv.net/member_illust.php?mode=medium&illust_id=82876433
 */
internal val URL_ARTWORK_REGEX = """(?<=pixiv\.net/(en/)?(i|artworks)/|illust_id=)\d+""".toRegex()

/**
 * https://www.pixiv.net/u/902077
 * https://www.pixiv.net/users/902077
 * https://www.pixiv.net/en/users/902077
 * https://www.pixiv.net/member.php?id=902077
 */
internal val URL_USER_REGEX = """(?<=pixiv\.net/(en/)?(u/|users/|member\.php\?id=))\d+""".toRegex()

/**
 * [リダイレクトURLサービス](https://www.pixiv.net/info.php?id=1554)
 *
 * https://pixiv.me/milkpanda-yellow
 */
internal val URL_PIXIV_ME_REGEX = """(?<=pixiv\.me/)[\w-]{3,32}""".toRegex()

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

@OptIn(InternalAPI::class)
internal val TAG_DELIMITERS = """_-&+|/\,()，、—（）""".toCharArray()

internal val CompletedJob: Job = Job().apply { complete() }

internal val MAX_RANGE = 0..999_999_999L

private const val OFFSET_STEP = 1_000_000L

internal val ALL_RANGE = (MAX_RANGE step OFFSET_STEP).map { offset -> offset until (offset + OFFSET_STEP) }