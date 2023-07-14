package xyz.cssxsh.mirai.pixiv

import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.mirai.pixiv.tools.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.fanbox.*
import xyz.cssxsh.pixiv.tool.*
import kotlin.math.*

internal val PIXIV_IMAGE_SOFTBANK = (134..147).map { "210.140.92.${it}" }

internal val PIXIV_API_SOFTBANK = ((199..223) + (224..229)).map { "210.140.131.${it}" }

internal val PIXIV_SKETCH_SOFTBANK = listOf("210.140.175.130", "210.140.174.37", "210.140.170.179")

internal val SAUCENAO_ORIGIN = listOf("45.32.0.237", "chr1.saucenao.com")

internal val PIXIV_RATE_LIMIT_DELAY: Long by lazy {
    System.getProperty("pixiv.rate.limit.delay")?.toLong() ?: (3 * 60 * 1000L)
}

internal val PIXIV_DOWNLOAD_ASYNC: Int by lazy {
    System.getProperty("pixiv.download.async")?.toInt() ?: 32
}

internal val PIXIV_HOST = mapOf(
    "*.pximg.net" to PIXIV_IMAGE_SOFTBANK,
    "*.pixiv.net" to PIXIV_API_SOFTBANK,
    "sketch.pixiv.net" to PIXIV_SKETCH_SOFTBANK,
    "*.saucenao.com" to SAUCENAO_ORIGIN
)

internal val DEFAULT_PIXIV_CONFIG: PixivConfig by lazy {
    PixivConfig(host = DEFAULT_PIXIV_HOST + PIXIV_HOST, proxy = ProxyApi)
}

internal fun initConfiguration() {
    CacheFolder.mkdirs()
    BackupFolder.mkdirs()
    TempFolder.mkdirs()
    ProfileFolder.mkdirs()
    ArticleFolder.mkdirs()
    UgoiraImagesFolder.mkdirs()
    OtherImagesFolder.mkdirs()
    ExistsImagesFolder.mkdirs()
    logger.info { "CacheFolder: ${CacheFolder.toPath().toUri()}" }
    logger.info { "BackupFolder: ${BackupFolder.toPath().toUri()}" }
    logger.info { "TempFolder: ${TempFolder.toPath().toUri()}" }
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

    ImageSearcher.key = ImageSearchConfig.key

    with(PixivGifConfig) {
        when {
            quantizer !in QUANTIZER_LIST -> {
                logger.warning { "PixivGifConfig.quantizer 非原生" }
            }
            "com.squareup.gifencoder.OctTreeQuantizer" != quantizer -> {
                logger.info { "目前GIF合成只有靠CPU算力，推荐使用 OctTreeQuantizer " }
            }
        }
        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
        System.setProperty(OpenCVQuantizer.MAX_COUNT, maxCount.toString())
        if (ditherer !in DITHERER_LIST) {
            logger.warning { "PixivGifConfig.ditherer 非原生" }
        }
    }

    factory.openSession().use { session ->
        create(session)
    }
    val count = ArtWorkInfo.count()
    if (count < EroChunk) {
        logger.warning { "缓存数 $count < ${EroChunk}，建议使用指令( /cache recommended )进行缓存" }
    } else {
        logger.info { "缓存数 $count " }
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
 * * `https://official.fanbox.cc/`
 * * `https://www.fanbox.cc/@official`
 */
internal val URL_FANBOX_CREATOR_REGEX get() = FanBoxCreator.URL_FANBOX_CREATOR_REGEX

/**
 * * `https://www.pixiv.net/fanbox/creator/31386013`
 */
internal val URL_FANBOX_ID_REGEX = """(?<=pixiv\.net/fanbox/creator/)\d+""".toRegex()

/**
 * * `https://www.pixivision.net/zh/a/6858`
 */
internal val URL_PIXIVISION_ARTICLE = """(?<=pixivision\.net/[\w-]{2,5}/a/)\d+""".toRegex()

/**
 * * `https://twitter.com/twitter`
 */
internal val URL_TWITTER_SCREEN = """(?<=twitter\.com/(#!/)?)\w{4,15}""".toRegex()


internal val DELETE_REGEX = """該当作品は削除されたか|作品已删除或者被限制|该作品已被删除，或作品ID不存在。""".toRegex()

internal const val PixivMirrorHost = "i.pixiv.re"

internal val MIN_SIMILARITY = sqrt(5.0).minus(1).div(2)

internal const val ERO_CHUNK = 16

internal const val ERO_UP_EXPIRE = 10 * 1000L

internal const val ERO_BOOKMARKS = 1L shl 12

internal const val ERO_PAGE_COUNT = 3

internal val ERO_TAG_EXCLUDE = """([hH]olo|僕のヒーローアカデミア)""".toRegex()

internal const val LOAD_LIMIT = 5_000L

internal const val TAG_TOP_LIMIT = 10

internal const val TAG_DELIMITERS = """_-&+|/\,()，、—（）"""

internal val MAX_RANGE = 0..999_999_999L

private const val OFFSET_STEP = 1_000_000L

internal val ALL_RANGE = (MAX_RANGE step OFFSET_STEP).map { offset -> offset until (offset + OFFSET_STEP) }