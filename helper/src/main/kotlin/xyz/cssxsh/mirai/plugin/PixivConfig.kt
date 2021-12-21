package xyz.cssxsh.mirai.plugin

import io.github.gnuf0rce.mirai.*
import io.ktor.client.features.*
import io.ktor.client.statement.*
import io.ktor.util.*
import kotlinx.coroutines.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.exception.*
import xyz.cssxsh.pixiv.tool.*
import java.io.*
import kotlin.math.*

typealias Ignore = suspend (Throwable) -> Boolean

internal val PIXIV_IMAGE_SOFTBANK = (134..147).map { "210.140.92.${it}" }

internal val PIXIV_API_SOFTBANK = ((199..223) + (224..229)).map { "210.140.131.${it}" }

internal val PIXIV_SKETCH_SOFTBANK = listOf("210.140.175.130", "210.140.174.37", "210.140.170.179")

internal val SAUCENAO_ORIGIN = listOf("45.32.0.237", "chr1.saucenao.com")

internal const val PIXIV_RATE_LIMIT_DELAY = 3 * 60 * 1000L

internal fun Ignore(client: PixivAuthClient): Ignore = { throwable ->
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
                    logger.warning { "PIXIV API OAuth 错误, 将刷新 Token $url with $request" }
                    try {
                        client.refresh()
                    } catch (cause: Throwable) {
                        logger.warning { "刷新 Token 失败 $cause" }
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

internal fun initConfiguration(scope: CoroutineScope) {
    CacheFolder.mkdirs()
    BackupFolder.mkdirs()
    TempFolder.mkdirs()
    ProfileFolder.mkdirs()
    ArticleFolder.mkdirs()
    UgoiraImagesFolder.mkdirs()
    ExistsImagesFolder.mkdirs()
    ExistsImagesFolder.mkdirs()
    logger.info { "CacheFolder: ${CacheFolder.absolutePath}" }
    logger.info { "BackupFolder: ${BackupFolder.absolutePath}" }
    logger.info { "TempFolder: ${TempFolder.absolutePath}" }
    if (ProxyMirror.isNotBlank()) {
        logger.warning { "镜像代理已开启 i.pximg.net -> $ProxyMirror 不推荐修改这个配置，建议保持留空" }
    }
    if (ProxyApi.isNotBlank()) {
        logger.warning { "已加载 API 代理 $ProxyApi API代理可能会导致SSL连接异常，请十分谨慎的开启这个功能" }
    }
    if (ProxyDownload.isNotBlank()) {
        logger.warning { "已加载 DOWNLOAD 代理 $ProxyDownload 图片下载器会对代理产生很大的负荷，请十分谨慎的开启这个功能" }
    }
    if (BlockSize <= 0) {
        logger.warning { "分块下载关闭，通常来说分块下载可以加快下载速度，建议开启，但分块不宜太小" }
    } else if (BlockSize < HTTP_KILO) {
        logger.warning { "下载分块过小" }
    }
    if (BackupUpload) {
        try {
            NetDisk
        } catch (exception: NoClassDefFoundError) {
            logger.warning { "相关类加载失败，请安装 https://github.com/gnuf0rce/Netdisk-FileSync-Plugin $exception" }
        }
    }

    ImageSearcher.key = ImageSearchConfig.key

    with(PixivGifConfig) {
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

    scope.launch {
        val count = ArtWorkInfo.count()
        if (count < EroChunk) {
            logger.warning { "缓存数 $count < ${EroChunk}，建议使用指令( /cache recommended )进行缓存" }
        } else {
            logger.info { "缓存数 $count " }
        }
    }
}

/**
 * * `https://www.pixiv.net/i/79695391`
 * * `https://www.pixiv.net/artworks/79695391`
 * * `https://www.pixiv.net/en/artworks/79695391`
 * * `https://www.pixiv.net/member_illust.php?mode=medium&illust_id=82876433`
 */
internal val URL_ARTWORK_REGEX = """(?<=pixiv\.net/(en/)?(i|artworks)/|illust_id=)\d+""".toRegex()

/**
 * * `https://www.pixiv.net/u/902077`
 * * `https://www.pixiv.net/users/902077`
 * * `https://www.pixiv.net/en/users/902077`
 * * `https://www.pixiv.net/member.php?id=902077`
 */
internal val URL_USER_REGEX = """(?<=pixiv\.net/(en/)?(u/|users/|member\.php\?id=))\d+""".toRegex()

/**
 * [リダイレクトURLサービス](https://www.pixiv.net/info.php?id=1554)
 * * `https://pixiv.me/milkpanda-yellow`
 */
internal val URL_PIXIV_ME_REGEX = """(?<=pixiv\.me/)[\w-]{3,32}""".toRegex()

/**
 * * `https://shiokojii.fanbox.cc/`
 */
internal val URL_FANBOX_CREATOR_REGEX = """([\w-]{3,16})(?=\.fanbox\.cc)""".toRegex()

/**
 * * `https://www.pixiv.net/fanbox/creator/31386013`
 */
internal val URL_FANBOX_ID_REGEX = """(?<=pixiv\.net/fanbox/creator/)\d+""".toRegex()

/**
 * * `https://www.pixivision.net/zh/a/6858`
 */
internal val URL_PIXIVISION_ARTICLE = """(?<=pixivision\.net/[\w-]{2,5}/a/)\d+""".toRegex()

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