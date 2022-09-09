package xyz.cssxsh.mirai.pixiv

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.pixiv.*
import java.io.*

// region PROPERTY

internal const val CACHE_FOLDER_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.cache"

internal const val BACKUP_FOLDER_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.backup"

internal const val TEMP_FOLDER_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.temp"

internal const val ERO_CHUNK_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.ero.chunk"

internal const val ERO_UP_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.ero.up"

internal const val ERO_SFW_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.ero.sfw"

internal const val ERO_STANDARD_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.ero.standard"

internal const val TAG_COOLING_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.tag.cooling"

internal const val TAG_SFW_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.tag.sfw"

internal const val CACHE_CAPACITY_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.cache.capacity"

internal const val CACHE_JUMP_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.cache.jump"

internal const val TIMEOUT_API_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.timeout.api"

internal const val TIMEOUT_DOWNLOAD_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.timeout.download"

internal const val PROXY_API_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.proxy.api"

internal const val PROXY_DOWNLOAD_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.proxy.download"

internal const val PROXY_MIRROR_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.proxy.mirror"

internal const val BLOCK_SIZE_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.block"

internal const val UPLOAD_PROPERTY = "xyz.cssxsh.mirai.plugin.pixiv.upload"

// endregion

/**
 * 1. [PixivHelperPlugin.logger]
 */
internal val logger by lazy {
    try {
        PixivHelperPlugin.logger
    } catch (_: ExceptionInInitializerError) {
        MiraiLogger.Factory.create(PixivHelper::class)
    }
}

/**
 * 1. [CACHE_FOLDER_PROPERTY]
 * 2. [PixivHelperSettings.cacheFolder]
 */
internal val CacheFolder by lazy {
    val path = System.getProperty(CACHE_FOLDER_PROPERTY)
    if (path.isNullOrBlank()) PixivHelperSettings.cacheFolder else File(path)
}

/**
 * 1. [BACKUP_FOLDER_PROPERTY]
 * 2. [PixivHelperSettings.backupFolder]
 */
internal val BackupFolder by lazy {
    val path = System.getProperty(BACKUP_FOLDER_PROPERTY)
    if (path.isNullOrBlank()) PixivHelperSettings.backupFolder else File(path)
}

/**
 * 1. [TEMP_FOLDER_PROPERTY]
 * 2. [PixivHelperSettings.tempFolder]
 */
internal val TempFolder by lazy {
    val path = System.getProperty(TEMP_FOLDER_PROPERTY)
    if (path.isNullOrBlank()) PixivHelperSettings.tempFolder else File(path)
}

/**
 * Task连续发送间隔时间
 * 1. [PixivConfigData.interval]
 */
internal val TaskSendInterval by PixivConfigData::interval

/**
 * Task通过转发发送
 * 1. [PixivConfigData.interval]
 */
internal val TaskForward by PixivConfigData::forward

/**
 * TODO TaskConut by PixivConfigData
 */
internal val TaskConut = 10

/**
 * 涩图防重复间隔
 * 1. [ERO_CHUNK_PROPERTY]
 * 2. [PixivHelperSettings.eroChunk]
 */
internal val EroChunk by lazy {
    System.getProperty(ERO_CHUNK_PROPERTY)?.toInt() ?: PixivHelperSettings.eroChunk
}

/**
 * 涩图提高收藏数时间
 * 1. [ERO_UP_PROPERTY]
 * 2. [PixivHelperSettings.eroUpExpire]
 */
internal val EroUpExpire by lazy {
    System.getProperty(ERO_UP_PROPERTY)?.toLong() ?: PixivHelperSettings.eroUpExpire
}

/**
 * 涩图标准
 * 1. [ERO_STANDARD_PROPERTY]
 * 2. [PixivHelperSettings]
 */
internal val EroStandard by lazy {
    System.getProperty(ERO_STANDARD_PROPERTY)?.let { PixivJson.decodeFromString(EroStandardData.serializer(), it) }
        ?: PixivHelperSettings
}

/**
 * TAG 年龄限制
 * 1. [TAG_SFW_PROPERTY]
 * 2. [PixivHelperSettings.tagSFW]
 */
internal val TagAgeLimit by lazy {
    if (System.getProperty(TAG_SFW_PROPERTY)?.toBoolean() ?: PixivHelperSettings.tagSFW) {
        AgeLimit.ALL
    } else {
        AgeLimit.R18G
    }
}

/**
 * ERO 年龄限制
 * 1. [ERO_SFW_PROPERTY]
 * 2. [PixivHelperSettings.eroSFW]
 */
internal val EroAgeLimit by lazy {
    if (System.getProperty(ERO_SFW_PROPERTY)?.toBoolean() ?: PixivHelperSettings.eroSFW) {
        AgeLimit.ALL
    } else {
        AgeLimit.R18G
    }
}

/**
 * 下载缓存容量，同时下载的图片任务上限
 * 1. [CACHE_CAPACITY_PROPERTY]
 * 2. [PixivHelperSettings.cacheCapacity]
 */
internal val CacheCapacity by lazy {
    System.getProperty(CACHE_CAPACITY_PROPERTY)?.toInt() ?: PixivHelperSettings.cacheCapacity
}

/**
 * 缓存是否跳过下载
 * 1. [CACHE_JUMP_PROPERTY]
 * 2. [PixivHelperSettings.cacheJump]
 */
internal val CacheJump by lazy {
    System.getProperty(CACHE_JUMP_PROPERTY)?.toBoolean() ?: PixivHelperSettings.cacheJump
}

/**
 * 1. [BACKUP_FOLDER_PROPERTY]
 * 2. [PixivHelperSettings.backupFolder]
 */
internal val BackupUpload by lazy {
    System.getProperty(UPLOAD_PROPERTY)?.toBoolean() ?: PixivHelperSettings.upload
}

/**
 * API超时时间, 单位ms
 * 1. [TIMEOUT_API_PROPERTY]
 * 2. [PixivHelperSettings.timeoutApi]
 */
internal val TimeoutApi by lazy {
    System.getProperty(TIMEOUT_API_PROPERTY)?.toLong() ?: PixivHelperSettings.timeoutApi
}

/**
 * DOWNLOAD超时时间, 单位ms
 * 1. [TIMEOUT_DOWNLOAD_PROPERTY]
 * 2. [PixivHelperSettings.timeoutDownload]
 */
internal val TimeoutDownload by lazy {
    System.getProperty(TIMEOUT_DOWNLOAD_PROPERTY)?.toLong() ?: PixivHelperSettings.timeoutDownload
}

/**
 * API代理
 * 1. [PROXY_API_PROPERTY]
 * 2. [PixivHelperSettings.proxyApi]
 */
internal val ProxyApi by lazy {
    System.getProperty(PROXY_API_PROPERTY) ?: PixivHelperSettings.proxyApi
}

/**
 * DOWNLOAD代理
 * 1. [PROXY_DOWNLOAD_PROPERTY]
 * 2. [PixivHelperSettings.proxyDownload]
 */
internal val ProxyDownload by lazy {
    System.getProperty(PROXY_DOWNLOAD_PROPERTY) ?: PixivHelperSettings.proxyDownload
}

/**
 * MIRROR代理
 * 1. [PROXY_MIRROR_PROPERTY]
 * 2. [PixivHelperSettings.pximg]
 */
internal val ProxyMirror by lazy {
    System.getProperty(PROXY_MIRROR_PROPERTY) ?: PixivHelperSettings.pximg
}

/**
 * DOWNLOAD分块大小, 单位B
 * 1. [BLOCK_SIZE_PROPERTY]
 * 2. [PixivHelperSettings.blockSize]
 */
internal val BlockSize by lazy {
    System.getProperty(BLOCK_SIZE_PROPERTY)?.toInt() ?: PixivHelperSettings.blockSize
}

/**
 * 用户文件保存目录
 */
internal val ProfileFolder: File get() = CacheFolder.resolve("profile")

/**
 * 特辑文件保存目录
 */
internal val ArticleFolder: File get() = CacheFolder.resolve("article")

/**
 * 图片目录
 */
internal fun images(pid: Long): File {
    return CacheFolder
        .resolve("%03d______".format(pid / 1_000_000))
        .resolve("%06d___".format(pid / 1_000))
        .resolve("$pid")
}

/**
 * GIF目录
 */
internal val UgoiraImagesFolder: File get() = TempFolder.resolve("gif")

/**
 * OTHER目录
 */
internal val OtherImagesFolder: File get() = TempFolder.resolve("other")

/**
 * EXISTS目录
 */
internal val ExistsImagesFolder: File get() = TempFolder.resolve("exists")

/**
 * 图片 JSON
 */
internal fun illust(pid: Long) = images(pid).resolve("${pid}.json")

/**
 * 动图 JSON
 */
internal fun ugoira(pid: Long) = images(pid).resolve("${pid}.ugoira.json")

public val Contact.helper: PixivHelper by PixivHelperPool

public fun CommandSender.client(): PixivClientPool.AuthClient {
    return when {
        isUser() -> PixivClientPool.get(id = subject.id)
        else -> PixivClientPool.console()
    } ?: throw IllegalArgumentException("未绑定 PIXIV 账号")
}