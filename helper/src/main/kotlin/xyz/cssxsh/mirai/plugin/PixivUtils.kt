package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.useSession
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.api.apps.*
import xyz.cssxsh.pixiv.api.publics.*
import xyz.cssxsh.pixiv.dao.*
import xyz.cssxsh.pixiv.data.apps.*
import xyz.cssxsh.pixiv.model.*
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime
import kotlin.time.Duration

/**
 * 获取对应subject的助手
 */
internal fun <T : MessageEvent> CommandSenderOnMessage<T>.getHelper() = PixivHelperManager[fromEvent.subject]

internal fun MessageEvent.getHelper() = PixivHelperManager[subject]

internal suspend fun <T : MessageEvent> CommandSenderOnMessage<T>.quoteReply(message: Message) =
    sendMessage(message + fromEvent.message.quote())

internal suspend fun <T : MessageEvent> CommandSenderOnMessage<T>.quoteReply(message: String) =
    quoteReply(message.toPlainText())

internal data class Mappers(
    val artwork: ArtWorkInfoMapper,
    val file: FileInfoMapper,
    val user: UserInfoMapper,
    val tag: TagInfoMapper,
    val statistic: StatisticInfoMapper,
    val delete: DeleteInfoMapper,
)

internal fun <T> useMappers(block: (Mappers) -> T) = useSession { session ->
    Mappers(
        artwork = session.getMapper(ArtWorkInfoMapper::class.java),
        file = session.getMapper(FileInfoMapper::class.java),
        user = session.getMapper(UserInfoMapper::class.java),
        tag = session.getMapper(TagInfoMapper::class.java),
        statistic = session.getMapper(StatisticInfoMapper::class.java),
        delete = session.getMapper(DeleteInfoMapper::class.java),
    ).let(block)
}

internal fun DeleteInfoMapper.add(pid: Long) = add(pid = pid, timestamp = OffsetDateTime.now().toEpochSecond())

internal fun UserPreview.isLoaded() = useMappers { mappers ->
    illusts.all { mappers.artwork.contains(it.pid) }
}

internal fun UserDetail.count() = useMappers { mapper ->
    mapper.artwork.countByUid(user.id)
}

internal fun UserDetail.total() = profile.totalIllusts + profile.totalManga

internal operator fun <V> Map<Boolean, V>.component1(): V? = get(true)

internal operator fun <V> Map<Boolean, V>.component2(): V? = get(false)

@MiraiInternalApi
internal fun Image.getMd5Hex(): String = md5.toUByteArray().joinToString("") {
    """%02x""".format(it.toInt())
}

internal fun Url.getFilename() = encodedPath.substringAfterLast('/')

internal fun IllustInfo.getContent(): Message = buildMessageChain {
    appendLine("作者: ${user.name} ")
    appendLine("UID: ${user.id} ")
    appendLine("标题: $title ")
    appendLine("PID: $pid ")
    appendLine("收藏数: $totalBookmarks ")
    appendLine("SAN值: $sanityLevel ")
    appendLine("创作于: $createAt ")
    appendLine("共: $pageCount 张图片 ")
    appendLine("标签：${tags.map { it.translatedName ?: it.name }}")
}

internal fun IllustInfo.getPixivCat(): Message = buildMessageChain {
    appendLine("原图连接: ")
    getPixivCatUrls().forEach {
        appendLine(it.toString())
    }
}

private const val TAG_MAX = 10

internal suspend fun PixivHelper.checkR18(illust: IllustInfo): IllustInfo {
    if (illust.sanityLevel == SanityLevel.BLACK && illust.isR18().not() && illust.tags.size == TAG_MAX) {
        logger.info { "正在检查PID: ${illust.pid} 是否为R18" }
        return when (getWork(illust.pid).works.single().ageLimit) {
            AgeLimit.ALL -> {
                illust
            }
            AgeLimit.R18 -> {
                illust.copy(tags = illust.tags + TagInfo(
                    name = "R-18"
                ))
            }
            AgeLimit.R18G -> {
                illust.copy(tags = illust.tags + TagInfo(
                    name = "R-18G"
                ))
            }
        }
    } else {
        return illust
    }
}

internal suspend fun PixivHelper.buildMessageByIllust(illust: IllustInfo, flush: Boolean): List<Message> = buildList {
    add(illust.getContent())
    if (link) {
        add(illust.getPixivCat())
    }
    val files = illust.getImages()
    if (illust.isR18().not()) {
        files.forEachIndexed { index, file ->
            if (index < PixivHelperSettings.eroPageCount) {
                add(file.uploadAsImage(contact))
            } else {
                logger.warning { "[${illust.pid}]图片过多，跳过{$index}" }
            }
        }
    } else {
        add(PlainText("R18禁止！"))
    }
    if (flush || useMappers { it.artwork.contains(illust.pid).not() }) {
        illust.saveToSQLite()
    }
}

internal suspend fun PixivHelper.buildMessageByIllust(pid: Long, flush: Boolean): List<Message> = buildMessageByIllust(
    illust = getIllustInfo(pid = pid, flush = flush),
    flush = flush
)

internal const val NO_PROFILE_IMAGE = "https://s.pximg.net/common/images/no_profile.png"

internal suspend fun PixivHelper.buildMessageByUser(detail: UserDetail, save: Boolean): MessageChain = buildMessageChain {
    appendLine("NAME: ${detail.user.name}")
    appendLine("UID: ${detail.user.id}")
    appendLine("ACCOUNT: ${detail.user.account}")
    appendLine("TOTAL: ${detail.total()}")
    appendLine("TWITTER: ${detail.profile.twitterAccount}")
    runCatching {
        // px16x16, px50x50, px170x170
        detail.user.profileImageUrls.getOrDefault("px16x16", NO_PROFILE_IMAGE).let { image ->
            PixivHelperSettings.profilesFolder.resolve(Url(image).getFilename()).apply {
                if (exists().not()) {
                    parentFile.mkdirs()
                    PixivHelperDownloader.downloadImages(
                        urls = listOf(image),
                        dir = parentFile
                    ).single().getOrThrow()
                }
            }
        }.let { file ->
            append(file.uploadAsImage(contact))
        }
    }.onFailure {
        logger.warning({ "User(${detail.user.id}) ProfileImage 下载失败" }, it)
    }
    if (save) {
        detail.saveToSQLite()
    }
}

internal suspend fun PixivHelper.buildMessageByUser(uid: Long, save: Boolean): MessageChain = buildMessageByUser(
    detail = userDetail(uid = uid),
    save = save
)

internal fun IllustInfo.getPixivCatUrls() = getOriginImageUrls().map {
    Url(it).copy(host = PixivMirrorHost)
}

internal fun IllustInfo.isR18(): Boolean =
    tags.any { """R-?18""".toRegex() in it.name }

internal fun IllustInfo.isEro(): Boolean =
    totalBookmarks ?: 0 >= PixivHelperSettings.eroBookmarks && pageCount < PixivHelperSettings.eroPageCount && type == WorkContentType.ILLUST

internal fun UserInfo.toUserBaseInfo() = UserBaseInfo(
    uid = id,
    name = name,
    account = account
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
        md5 = PixivHelperSettings.imagesFolder(pid).resolve(Url(url).getFilename()).readBytes().getMd5(),
        url = url,
        size = PixivHelperSettings.imagesFolder(pid).resolve(Url(url).getFilename()).length()
    )
}

internal fun IllustInfo.getTagInfo() = tags.map {
    TagBaseInfo(
        pid = pid,
        name = it.name,
        translatedName = it.translatedName
    )
}

internal fun UserInfoMapper.addUserByIllustInfo(user: UserBaseInfo) =
    if (findByUid(user.uid) != null) updateUser(user) else replaceUser(user)

internal fun IllustInfo.saveToSQLite(): Unit = useSession { session ->
    session.getMapper(UserInfoMapper::class.java).addUserByIllustInfo(user.toUserBaseInfo())
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
            mapper.addUserByIllustInfo(info.user.toUserBaseInfo())
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

internal fun UserDetail.saveToSQLite(): Unit = useMappers { mapper ->
    mapper.user.addUserByIllustInfo(user.toUserBaseInfo())
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

internal fun File.readIllustInfo(): IllustInfo =
    Json_.decodeFromString(IllustInfo.serializer(), readText())

internal fun IllustInfo.writeToCache(file: File = PixivHelperSettings.imagesFolder(pid).resolve("${pid}.json")) =
    apply { file.apply { parentFile.mkdirs() }.writeText(Json_.encodeToString(IllustInfo.serializer(), this)) }

internal fun List<IllustInfo>.writeToCache() = onEach { it.writeToCache() }

internal suspend fun PixivHelper.getIllustInfo(
    pid: Long,
    flush: Boolean,
    block: suspend PixivHelper.(Long) -> IllustInfo = { illustDetail(it).illust },
): IllustInfo = PixivHelperSettings.imagesFolder(pid).resolve("${pid}.json").let { file ->
    if (!flush && file.exists()) {
        file.readIllustInfo()
    } else {
        block(pid).writeToCache(file = file)
    }
}

internal fun Throwable.isNotCancellationException() =
    (this is CancellationException || message == "No more continuations to resume").not()

internal suspend fun IllustInfo.getImages(): List<File> {
    val dir = PixivHelperSettings.imagesFolder(pid)
    val temp = PixivHelperSettings.tempFolder
    val downloads = mutableListOf<String>()
    dir.mkdirs()
    val files = getOriginImageUrls().map { url ->
        dir.resolve(Url(url).getFilename()).apply {
            if (exists().not() && temp.resolve(name).exists()) {
                logger.info { "从[${temp.resolve(name)}]移动文件" }
                temp.resolve(name).renameTo(this)
            }
            if (exists().not()) {
                downloads.add(url)
            }
        }
    }
    if (downloads.isNotEmpty()) {
        check(PixivHelperDownloader.downloadImages(downloads, dir).all { it.isSuccess })
        logger.info { "作品(${pid})<${createAt}>[${type}][${user.id}][${title}][${downloads.size}]{${totalBookmarks}}下载完成" }
    }
    return files
}

internal fun getBackupList() = buildMap<String, File> {
    this["DATA"] = PixivHelperPlugin.dataFolder
    this["CONFIG"] = PixivHelperPlugin.configFolder
    val lastBackup: Long = PixivHelperSettings.backupFolder.listFiles { file ->
        file.name.startsWith("DATABASE")
    }?.maxOf { it.lastModified() } ?: System.currentTimeMillis()
    if (PixivHelperSettings.sqlite.lastModified() > lastBackup) {
        this["DATABASE"] = PixivHelperSettings.sqlite
    }
}

internal fun MessageSource.inDuration(duration: Duration) =
    time > OffsetDateTime.now().toEpochSecond() - duration.inSeconds.toLong()