package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.network.sockets.*
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.uploadAsImage
import net.mamoe.mirai.utils.*
import okhttp3.internal.http2.StreamResetException
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.imagesFolder
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.useSession
import xyz.cssxsh.pixiv.WorkContentType
import xyz.cssxsh.pixiv.api.app.illustDetail
import xyz.cssxsh.pixiv.dao.*
import xyz.cssxsh.pixiv.data.app.IllustInfo
import xyz.cssxsh.pixiv.model.*
import xyz.cssxsh.pixiv.tool.downloadImageUrls
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.security.MessageDigest
import javax.net.ssl.SSLException

/**
 * 获取对应subject的助手
 */
fun <T : MessageEvent> CommandSenderOnMessage<T>.getHelper() = PixivHelperManager[fromEvent.subject]

fun <T> useArtWorkInfoMapper(block: (ArtWorkInfoMapper) -> T) = useSession { session ->
    session.getMapper(ArtWorkInfoMapper::class.java).let(block)
}

fun <T> useFileInfoMapper(block: (FileInfoMapper) -> T) = useSession { session ->
    session.getMapper(FileInfoMapper::class.java).let(block)
}

fun <T> useUserInfoMapper(block: (UserInfoMapper) -> T) = useSession { session ->
    session.getMapper(UserInfoMapper::class.java).let(block)
}

fun <T> useTagInfoMapper(block: (TagInfoMapper) -> T) = useSession { session ->
    session.getMapper(TagInfoMapper::class.java).let(block)
}

fun String.getFilename() = substring(lastIndexOfAny(listOf("\\", "/")) + 1)

fun IllustInfo.getMessage(): Message = buildMessageChain {
    appendLine("作者: ${user.name} ")
    appendLine("UID: ${user.id} ")
    appendLine("收藏数: $totalBookmarks ")
    appendLine("SAN值: $sanityLevel ")
    appendLine("创作于: $createDate ")
    appendLine("共: $pageCount 张图片 ")
    appendLine("Pixiv_Net: https://www.pixiv.net/artworks/${pid} ")
    appendLine("标签：${tags.map { it.translatedName ?: it.name }}")
}

fun ArtWorkInfo.getMessage(): Message = buildMessageChain {
    appendLine("作者: ${useUserInfoMapper { it.findByUid(uid) }?.name}")
    appendLine("UID: $uid ")
    appendLine("收藏数: $totalBookmarks ")
    appendLine("SAN值: $sanityLevel ")
    appendLine("创作于: $createDate ")
    appendLine("共: $pageCount 张图片 ")
    appendLine("Pixiv_Net: https://www.pixiv.net/artworks/${pid} ")
    appendLine("标签：${useTagInfoMapper { it.findByPid(pid) }.map { it.translatedName ?: it.name }}")
}

suspend fun PixivHelper.buildMessage(
    illust: IllustInfo,
    save: Boolean = true
): List<Message> = buildList {
    if (simpleInfo) {
        add(buildMessageChain {
            appendLine("PID: ${illust.pid} ")
            appendLine("UID: ${illust.user.id} ")
            appendLine("收藏数: ${illust.totalBookmarks} ")
            appendLine("SAN值: ${illust.sanityLevel} ")
        })
    } else {
        add(illust.getMessage())
        add(buildMessageChain {
            appendLine("原图连接: ")
            illust.getPixivCatUrls().forEach {
                appendLine(it)
            }
        })
    }
    if (!illust.isR18()) {
        getImages(pid = illust.pid, urls = illust.getOriginUrl()).forEach {
            add(it.uploadAsImage(contact))
        }
    } else {
        add(PlainText("R18禁止！"))
    }
    if (save) {
        illust.saveToSQLite()
    }
}

suspend fun PixivHelper.buildMessage(
    info: ArtWorkInfo,
): List<Message> = buildList {
    if (simpleInfo) {
        add(buildMessageChain {
            appendLine("PID: ${info.pid} ")
            appendLine("UID: ${info.uid} ")
            appendLine("收藏数: ${info.totalBookmarks} ")
            appendLine("SAN值: ${info.sanityLevel} ")
        })
    } else {
        add(info.getMessage())
    }
    if (!info.isR18) {
        getImages(pid = info.pid, urls = useFileInfoMapper { it.fileInfos(info.pid) }.map { it.url }).forEach {
            add(it.uploadAsImage(contact))
        }
    } else {
        add(PlainText("R18禁止！"))
    }
}

fun IllustInfo.getPixivCatUrls(): List<String> = buildList {
    if (pageCount > 1) {
        (1..pageCount).forEach {
            add("https://pixiv.cat/${pid}-${it}.jpg")
        }
    } else {
        add("https://pixiv.cat/${pid}.jpg")
    }
}

fun IllustInfo.isR18(): Boolean =
    tags.any { """R-?18""".toRegex() in it.name || "精液" in it.name || "中出" in it.name }

fun IllustInfo.isEro(): Boolean =
    totalBookmarks ?: 0 >= PixivHelperSettings.totalBookmarks && pageCount < 4 && type == WorkContentType.ILLUST

fun IllustInfo.saveToSQLite(): Unit = useSession { session ->
    session.getMapper(UserInfoMapper::class.java).insertUser(UserInfo(
        uid = user.id,
        name = user.name,
        account = user.account
    ))
    session.getMapper(ArtWorkInfoMapper::class.java).insertArtWork(ArtWorkInfo(
        pid = pid,
        uid = user.id,
        title = title,
        caption = caption,
        createDate = createDate,
        pageCount = pageCount,
        sanityLevel = sanityLevel,
        type = type.value(),
        width = width,
        height = height,
        totalBookmarks = totalBookmarks ?: 0,
        totalComments = totalComments ?: 0,
        totalView = totalView ?: 0,
        isR18 = isR18(),
        isEro = isEro()
    ))
    session.getMapper(FileInfoMapper::class.java).insertFiles(getOriginUrl().mapIndexed { index, url ->
        FileInfo(
            pid = pid,
            index = index,
            md5 = imagesFolder(pid).resolve(url.getFilename()).readBytes().getMd5(),
            url = url,
            size = imagesFolder(pid).resolve(url.getFilename()).length()
        )
    })
    if (tags.isNotEmpty()) {
        session.getMapper(TagInfoMapper::class.java).insertTags(tags.map {
            TagInfo(
                pid = pid,
                name = it.name,
                translatedName = it.translatedName
            )
        })
    }
    logger.info { "作品(${pid})<${createDate}>[${user.id}][${type}][${title}][${pageCount}]{${totalBookmarks}}信息已设置" }
}

fun Collection<IllustInfo>.saveToSQLite(): Unit = useSession { session ->
    logger.verbose { "作品(${first().pid}..${last().pid})信息即将写入已设置" }
    session.getMapper(UserInfoMapper::class.java).insertUsers(buildMap<Long, UserInfo> {
        this@saveToSQLite.forEach { info ->
            putIfAbsent(info.user.id, UserInfo(
                uid = info.user.id,
                name = info.user.name,
                account = info.user.account
            ))
        }
    }.values.toList())
    logger.verbose { "作品{${first().pid}..${last().pid}}用户信息写入已设置" }
    forEach { info ->
        session.getMapper(ArtWorkInfoMapper::class.java).insertArtWork(ArtWorkInfo(
            pid = info.pid,
            uid = info.user.id,
            title = info.title,
            caption = info.caption,
            createDate = info.createDate,
            pageCount = info.pageCount,
            sanityLevel = info.sanityLevel,
            type = info.type.value(),
            width = info.width,
            height = info.height,
            totalBookmarks = info.totalBookmarks ?: 0,
            totalComments = info.totalComments ?: 0,
            totalView = info.totalView ?: 0,
            isR18 = info.isR18(),
            isEro = info.isEro()
        ))
    }
    logger.verbose { "作品{${first().pid}..${last().pid}}基础信息已写入设置" }
    forEach { info ->
        session.getMapper(FileInfoMapper::class.java).insertFiles(info.getOriginUrl().mapIndexed { index, url ->
            FileInfo(
                pid = info.pid,
                index = index,
                md5 = imagesFolder(info.pid).resolve(url.getFilename()).readBytes().getMd5(),
                url = url,
                size = imagesFolder(info.pid).resolve(url.getFilename()).length()
            )
        })
    }
    logger.verbose { "作品{${first().pid}..${last().pid}}文件信息已写入设置" }
    forEach { info ->
        if (info.tags.isNotEmpty()) {
            session.getMapper(TagInfoMapper::class.java).insertTags(info.tags.map {
                TagInfo(
                    pid = info.pid,
                    name = it.name,
                    translatedName = it.translatedName
                )
            })
        }
    }
    logger.verbose { "作品{${first().pid}..${last().pid}}标签信息已写入设置" }
    logger.info { "作品{${first().pid}..${last().pid}}信息已写入设置" }
}

fun ByteArray.getMd5(): String =
    MessageDigest.getInstance("md5").digest(this).asUByteArray().joinToString("") {
        """%02x""".format(it.toInt())
    }

internal val Json_ = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
    allowStructuredMapKeys = true
}

fun IllustInfo.writeTo(
    file: File,
) = file.apply { parentFile.mkdirs() }.writeText(
    Json_.encodeToString(IllustInfo.serializer(), this)
)

fun File.readIllustInfo(): IllustInfo = readText().let {
    Json_.decodeFromString(IllustInfo.serializer(), it)
}

fun IllustInfo.writeToCache() = writeTo(imagesFolder(pid).resolve("${pid}.json"))

fun Collection<IllustInfo>.writeToCache() = forEach { illust ->
    illust.writeToCache()
}

suspend fun PixivHelper.getIllustInfo(
    pid: Long,
    flush: Boolean = false,
    block: suspend PixivHelper.(Long) -> IllustInfo = { illustDetail(it).illust },
): IllustInfo = imagesFolder(pid).let { dir ->
    dir.resolve("${pid}.json").let { file ->
        if (!flush && file.exists()) {
            file.readIllustInfo()
        } else {
            block(pid).apply {
                writeTo(file)
            }
        }
    }
}

suspend fun PixivHelper.downloadImageUrls(urls: List<String>, dir: File): List<Result<File>> = downloadImageUrls(
    urls = urls,
    ignore = { url, throwable ->
        when (throwable) {
            is SSLException,
            is EOFException,
            is ConnectException,
            is SocketTimeoutException,
            is HttpRequestTimeoutException,
            is StreamResetException,
            -> {
                logger.warning { "[${url}]下载错误, 已忽略: ${throwable.message}" }
                true
            }
            else -> false
        }
    },
    block = { _, url, result ->
        runCatching {
            dir.resolve(url.getFilename()).apply {
                writeBytes(result.getOrThrow())
            }
        }
    }
)

suspend fun PixivHelper.getImages(
    pid: Long,
    urls: List<String>,
): List<File> = imagesFolder(pid).let { dir ->
    urls.filter { dir.resolve(it.getFilename()).exists().not() }.takeIf { it.isNotEmpty() }?.let { downloads ->
        dir.mkdirs()
        downloadImageUrls(urls = downloads, dir = dir).all { result ->
            result.onFailure {
                logger.warning({ "作品(${pid})下载错误" }, it)
            }.isSuccess
        }.let {
            check(it) { "作品(${pid})下载错误" }
        }
    }
    urls.map { url ->
        dir.resolve(url.getFilename())
    }
}