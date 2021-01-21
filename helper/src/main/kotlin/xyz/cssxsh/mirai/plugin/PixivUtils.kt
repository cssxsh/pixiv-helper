package xyz.cssxsh.mirai.plugin

import io.ktor.client.features.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.uploadAsImage
import net.mamoe.mirai.utils.*
import okhttp3.internal.http2.StreamResetException
import xyz.cssxsh.mirai.plugin.PixivHelperDownloader.getImages
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.imagesFolder
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.useSession
import xyz.cssxsh.pixiv.WorkContentType
import xyz.cssxsh.pixiv.api.app.illustDetail
import xyz.cssxsh.pixiv.dao.*
import xyz.cssxsh.pixiv.data.app.IllustInfo
import xyz.cssxsh.pixiv.model.*
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
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

suspend fun PixivHelper.buildMessageByIllust(
    illust: IllustInfo,
    save: Boolean = true,
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

suspend fun PixivHelper.buildMessageByIllust(
    pid: Long,
): List<Message> = buildMessageByIllust(
    illust = getIllustInfo(pid),
    save = false
)

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
    tags.any { """R-?18""".toRegex() in it.name }

fun IllustInfo.isEro(): Boolean =
    totalBookmarks ?: 0 >= PixivHelperSettings.totalBookmarks && pageCount < 4 && type == WorkContentType.ILLUST

fun IllustInfo.getUserInfo() = UserInfo(
    uid = user.id,
    name = user.name,
    account = user.account
)

fun IllustInfo.getArtWorkInfo() = ArtWorkInfo(
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
)

fun IllustInfo.getFileInfos() = getOriginUrl().mapIndexed { index, url ->
    FileInfo(
        pid = pid,
        index = index,
        md5 = imagesFolder(pid).resolve(url.getFilename()).readBytes().getMd5(),
        url = url,
        size = imagesFolder(pid).resolve(url.getFilename()).length()
    )
}

fun IllustInfo.getTagInfo() = tags.map {
    TagInfo(
        pid = pid,
        name = it.name,
        translatedName = it.translatedName
    )
}

internal fun UserInfoMapper.addUserByIllustInfo(user: UserInfo) =
    if (findByUid(user.uid) != null) updateUser(user) else replaceUser(user)

fun IllustInfo.saveToSQLite(): Unit = useSession { session ->
    session.getMapper(UserInfoMapper::class.java).addUserByIllustInfo(getUserInfo())
    session.getMapper(ArtWorkInfoMapper::class.java).replaceArtWork(getArtWorkInfo())
    session.getMapper(FileInfoMapper::class.java).replaceFiles(getFileInfos())
    if (tags.isNotEmpty()) {
        session.getMapper(TagInfoMapper::class.java).replaceTags(getTagInfo())
    }
    logger.info { "作品(${pid})<${createDate}>[${user.id}][${type}][${title}][${pageCount}]{${totalBookmarks}}信息已设置" }
}

fun Collection<IllustInfo>.updateToSQLite(): Unit = useSession { session ->
    logger.verbose { "作品(${first().pid..last().pid})[${size}]信息即将更新" }

    session.getMapper(ArtWorkInfoMapper::class.java).let { mapper ->
        this@updateToSQLite.forEach { info ->
            mapper.updateArtWork(info.getArtWorkInfo())
        }
    }
    logger.verbose { "作品{${first().pid..last().pid}}[${size}]基础信息已更新" }

    session.getMapper(TagInfoMapper::class.java).let { mapper ->
        this@updateToSQLite.forEach { info ->
            if (info.tags.isNotEmpty()) {
                mapper.replaceTags(info.getTagInfo())
            }
        }
    }

    logger.verbose { "作品{${first().pid..last().pid}}[${size}]标签信息已更新" }

    logger.info { "作品{${first().pid..last().pid}}[${size}]信息已更新" }
}

fun Collection<IllustInfo>.saveToSQLite(): Unit = useSession { session ->
    logger.verbose { "作品(${first().pid..last().pid})[${size}]信息即将插入" }

    session.getMapper(UserInfoMapper::class.java).let { mapper ->
        this@saveToSQLite.forEach { info ->
            mapper.addUserByIllustInfo(info.getUserInfo())
        }
    }
    logger.verbose { "作品{${first().pid..last().pid}}[${size}]用户信息已插入" }

    session.getMapper(ArtWorkInfoMapper::class.java).let { mapper ->
        this@saveToSQLite.forEach { info ->
            mapper.replaceArtWork(info.getArtWorkInfo())
        }
    }
    logger.verbose { "作品{${first().pid..last().pid}}[${size}]基础信息已插入" }

    session.getMapper(FileInfoMapper::class.java).let { mapper ->
        this@saveToSQLite.forEach { info ->
            mapper.replaceFiles(info.getFileInfos())
        }
    }
    logger.verbose { "作品{${first().pid..last().pid}}[${size}]文件信息已插入" }

    session.getMapper(TagInfoMapper::class.java).let { mapper ->
        this@saveToSQLite.forEach { info ->
            if (info.tags.isNotEmpty()) {
                mapper.replaceTags(info.getTagInfo())
            }
        }
    }
    logger.verbose { "作品{${first().pid..last().pid}}[${size}]标签信息已插入" }

    logger.info { "作品{${first().pid..last().pid}}[${size}]信息已插入" }
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

fun IllustInfo.writeTo(file: File, ) =
    file.apply { parentFile.mkdirs() }.writeText(Json_.encodeToString(IllustInfo.serializer(), this))

fun File.readIllustInfo(): IllustInfo =
    Json_.decodeFromString(IllustInfo.serializer(), readText())

fun IllustInfo.writeToCache() =
    writeTo(imagesFolder(pid).resolve("${pid}.json"))

fun Collection<IllustInfo>.writeToCache() = forEach { illust ->
    illust.writeToCache()
}

val apiIgnore: suspend (Throwable) -> Boolean = { throwable ->
    when (throwable) {
        is SSLException,
        is EOFException,
        is ConnectException,
        is SocketTimeoutException,
        is HttpRequestTimeoutException,
        is StreamResetException,
        is UnknownHostException,
        -> {
            logger.warning { "API错误, 已忽略: ${throwable.message}" }
            true
        }
        else -> when (throwable.message) {
            "Required SETTINGS preface not received" -> {
                logger.warning { "API错误, 已忽略: ${throwable.message}" }
                true
            }
            "Rate Limit" -> {
                logger.warning { "API限流, 已延时: ${throwable.message}" }
                delay((10).minutesToMillis)
                true
            }
            else -> false
        }
    }
}

suspend fun PixivHelper.getIllustInfo(
    pid: Long,
    flush: Boolean = false,
    block: suspend PixivHelper.(Long) -> IllustInfo = { illustDetail(pid = it, ignore = apiIgnore).illust },
): IllustInfo = imagesFolder(pid).resolve("${pid}.json").let { file ->
    if (!flush && file.exists()) {
        file.readIllustInfo()
    } else {
        block(pid).apply {
            writeTo(file)
        }
    }
}
