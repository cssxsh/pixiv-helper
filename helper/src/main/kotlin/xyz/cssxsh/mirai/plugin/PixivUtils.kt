package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.imagesFolder
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.useSession
import xyz.cssxsh.pixiv.WorkContentType
import xyz.cssxsh.pixiv.api.apps.illustDetail
import xyz.cssxsh.pixiv.dao.*
import xyz.cssxsh.pixiv.data.apps.IllustInfo
import xyz.cssxsh.pixiv.model.*
import java.io.File
import java.security.MessageDigest

/**
 * 获取对应subject的助手
 */
internal fun <T : MessageEvent> CommandSenderOnMessage<T>.getHelper() = PixivHelperManager[fromEvent.subject]

internal suspend fun <T : MessageEvent> CommandSenderOnMessage<T>.quoteReply(message: Message) =
    sendMessage(message + fromEvent.message.quote())

internal suspend fun <T : MessageEvent> CommandSenderOnMessage<T>.quoteReply(message: String) =
    quoteReply(message.toPlainText())

internal fun <T> useArtWorkInfoMapper(block: (ArtWorkInfoMapper) -> T) = useSession { session ->
    session.getMapper(ArtWorkInfoMapper::class.java).let(block)
}

internal fun <T> useFileInfoMapper(block: (FileInfoMapper) -> T) = useSession { session ->
    session.getMapper(FileInfoMapper::class.java).let(block)
}

internal fun <T> useUserInfoMapper(block: (UserInfoMapper) -> T) = useSession { session ->
    session.getMapper(UserInfoMapper::class.java).let(block)
}

internal fun <T> useTagInfoMapper(block: (TagInfoMapper) -> T) = useSession { session ->
    session.getMapper(TagInfoMapper::class.java).let(block)
}

operator fun <V> Map<Boolean, V>.component1(): V? = get(true)

operator fun <V> Map<Boolean, V>.component2(): V? = get(false)

@MiraiInternalApi
internal fun Image.getMd5Hex(): String = md5.toUByteArray().joinToString("") {
    """%02x""".format(it.toInt())
}

internal fun Url.getFilename() = encodedPath.substring(encodedPath.lastIndexOfAny(listOf("\\", "/")) + 1)

internal fun IllustInfo.getMessage(): Message = buildMessageChain {
    appendLine("作者: ${user.name} ")
    appendLine("UID: ${user.id} ")
    appendLine("收藏数: $totalBookmarks ")
    appendLine("SAN值: $sanityLevel ")
    appendLine("创作于: $createAt ")
    appendLine("共: $pageCount 张图片 ")
    appendLine("Pixiv_Net: https://www.pixiv.net/artworks/${pid} ")
    appendLine("标签：${tags.map { it.translatedName ?: it.name }}")
}

internal fun IllustInfo.getSimpleMessage(): Message = buildMessageChain {
    appendLine("PID: $pid ")
    appendLine("UID: ${user.id} ")
    appendLine("收藏数: $totalBookmarks ")
    appendLine("SAN值: $sanityLevel ")
}

internal fun IllustInfo.getPixivCat(): Message = buildMessageChain {
    appendLine("原图连接: ")
    getPixivCatUrls().forEach {
        appendLine(it)
    }
}

internal suspend fun PixivHelper.buildMessageByIllust(
    illust: IllustInfo,
    save: Boolean = true,
): List<Message> = buildList {
    if (simpleInfo) {
        add(illust.getSimpleMessage())
    } else {
        add(illust.getMessage())
        add(illust.getPixivCat())
    }
    if (!illust.isR18()) {
        illust.getImages().forEach {
            add(it.uploadAsImage(contact))
        }
    } else {
        add(PlainText("R18禁止！"))
    }
    if (save) {
        illust.saveToSQLite()
    }
}

internal suspend fun PixivHelper.buildMessageByIllust(
    pid: Long,
): List<Message> = buildMessageByIllust(
    illust = getIllustInfo(pid),
    save = false
)

internal fun IllustInfo.getPixivCatUrls(): List<String> = buildList {
    if (pageCount > 1) {
        (1..pageCount).forEach {
            add("https://pixiv.cat/${pid}-${it}.jpg")
        }
    } else {
        add("https://pixiv.cat/${pid}.jpg")
    }
}

internal fun IllustInfo.isR18(): Boolean =
    tags.any { """R-?18""".toRegex() in it.name }

internal fun IllustInfo.isEro(): Boolean =
    totalBookmarks ?: 0 >= PixivHelperSettings.totalBookmarks && pageCount < 4 && type == WorkContentType.ILLUST

internal fun IllustInfo.getUserInfo() = UserInfo(
    uid = user.id,
    name = user.name,
    account = user.account
)

internal fun IllustInfo.getArtWorkInfo() = ArtWorkInfo(
    pid = pid,
    uid = user.id,
    title = title,
    caption = caption,
    createAt = createAt.toEpochSecond(),
    pageCount = pageCount,
    sanityLevel = sanityLevel.ordinal,
    type = type.value(),
    width = width,
    height = height,
    totalBookmarks = totalBookmarks ?: 0,
    totalComments = totalComments ?: 0,
    totalView = totalView ?: 0,
    isR18 = isR18(),
    isEro = isEro()
)

internal fun IllustInfo.getFileInfos() = getOriginImageUrls().mapIndexed { index, url ->
    FileInfo(
        pid = pid,
        index = index,
        md5 = imagesFolder(pid).resolve(Url(url).getFilename()).readBytes().getMd5(),
        url = url,
        size = imagesFolder(pid).resolve(Url(url).getFilename()).length()
    )
}

internal fun IllustInfo.getTagInfo() = tags.map {
    TagInfo(
        pid = pid,
        name = it.name,
        translatedName = it.translatedName
    )
}

internal fun UserInfoMapper.addUserByIllustInfo(user: UserInfo) =
    if (findByUid(user.uid) != null) updateUser(user) else replaceUser(user)

internal fun IllustInfo.saveToSQLite(): Unit = useSession { session ->
    session.getMapper(UserInfoMapper::class.java).addUserByIllustInfo(getUserInfo())
    session.getMapper(ArtWorkInfoMapper::class.java).replaceArtWork(getArtWorkInfo())
    session.getMapper(FileInfoMapper::class.java).replaceFiles(getFileInfos())
    if (tags.isNotEmpty()) {
        session.getMapper(TagInfoMapper::class.java).replaceTags(getTagInfo())
    }
    logger.info { "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${pageCount}]{${totalBookmarks}}信息已设置" }
}

internal fun Collection<IllustInfo>.updateToSQLite(): Unit = useSession { session ->
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

internal fun Collection<IllustInfo>.saveToSQLite(): Unit = useSession { session ->
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

internal fun ByteArray.getMd5(): String =
    MessageDigest.getInstance("md5").digest(this).asUByteArray().joinToString("") {
        """%02x""".format(it.toInt())
    }

internal val Json_ = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
    allowStructuredMapKeys = true
}

internal fun IllustInfo.writeTo(file: File) =
    file.apply { parentFile.mkdirs() }.writeText(Json_.encodeToString(IllustInfo.serializer(), this))

internal fun File.readIllustInfo(): IllustInfo =
    Json_.decodeFromString(IllustInfo.serializer(), readText())

internal fun IllustInfo.writeToCache() =
    writeTo(imagesFolder(pid).resolve("${pid}.json"))

internal fun Iterable<IllustInfo>.writeToCache() = forEach { illust ->
    illust.writeToCache()
}

internal suspend fun PixivHelper.getIllustInfo(
    pid: Long,
    flush: Boolean = false,
    block: suspend PixivHelper.(Long) -> IllustInfo = { illustDetail(it).illust },
): IllustInfo = imagesFolder(pid).resolve("${pid}.json").let { file ->
    if (!flush && file.exists()) {
        file.readIllustInfo()
    } else {
        block(pid).apply {
            writeTo(file)
        }
    }
}

internal suspend fun IllustInfo.getImages(): List<File> = imagesFolder(pid).let { dir ->
    getOriginImageUrls().filter { dir.resolve(Url(it).getFilename()).exists().not() }.takeIf { it.isNotEmpty() }?.let { downloads ->
        dir.mkdirs()
        PixivHelperDownloader.downloadImageUrls(downloads, dir).let { list ->
            check(list.all { it.isSuccess }) {
                "作品(${pid})下载错误, ${list.mapNotNull { it.exceptionOrNull()?.message }}"
            }
        }
        logger.info { "作品(${pid})<${createAt}>[${type}][${user.id}][${title}][${downloads.size}]{${totalBookmarks}}下载完成" }
    }
    getOriginImageUrls().map { url ->
        dir.resolve(Url(url).getFilename())
    }
}