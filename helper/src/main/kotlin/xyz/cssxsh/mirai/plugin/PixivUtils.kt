package xyz.cssxsh.mirai.plugin

import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.dao.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.seconds

internal val logger get() = PixivHelperPlugin.logger

internal suspend fun CommandSenderOnMessage<*>.withHelper(block: suspend PixivHelper.() -> Any?): Boolean {
    return runCatching {
        PixivHelperManager[fromEvent.subject].block()
    }.onSuccess { message ->
        when (message) {
            null, Unit -> Unit
            is Message -> quoteReply(message)
            is String -> quoteReply(message)
            else -> quoteReply(message.toString())
        }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}

internal suspend fun CommandSenderOnMessage<*>.sendIllust(
    flush: Boolean,
    block: suspend PixivHelper.() -> IllustInfo,
): Boolean {
    return runCatching {
        PixivHelperManager[fromEvent.subject].run {
            buildMessageByIllust(illust = block(), flush = flush)
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        logger.warning({ "读取色图失败" }, it)
        quoteReply("读取色图失败， ${it.message}")
    }.isSuccess
}

internal suspend fun CommandSenderOnMessage<*>.sendIllust(
    block: suspend PixivHelper.() -> ArtWorkInfo,
) = sendIllust(flush = false) {
    getIllustInfo(pid = block().pid, flush = false)
}

/**
 * 连续发送间隔时间
 */
internal val interval get() = PixivConfigData.interval.seconds

internal suspend fun CommandSenderOnMessage<*>.quoteReply(message: Message) =
    sendMessage(message + fromEvent.message.quote())

internal suspend fun CommandSenderOnMessage<*>.quoteReply(message: String) =
    quoteReply(message.toPlainText())

internal data class Mappers(
    val artwork: ArtWorkInfoMapper,
    val file: FileInfoMapper,
    val user: UserInfoMapper,
    val tag: TagInfoMapper,
    val statistic: StatisticInfoMapper,
)

internal fun <T> useMappers(block: (Mappers) -> T) = PixivHelperPlugin.useSession { session ->
    Mappers(
        artwork = session.getMapper(ArtWorkInfoMapper::class.java),
        file = session.getMapper(FileInfoMapper::class.java),
        user = session.getMapper(UserInfoMapper::class.java),
        tag = session.getMapper(TagInfoMapper::class.java),
        statistic = session.getMapper(StatisticInfoMapper::class.java)
    ).let(block)
}

internal fun UserPreview.isLoaded() = useMappers { mappers -> illusts.all { mappers.artwork.contains(it.pid) } }

internal fun UserInfo.count() = useMappers { mapper -> mapper.artwork.countByUid(id) }

internal fun UserDetail.total() = profile.totalIllusts + profile.totalManga

internal operator fun <V> Map<Boolean, V>.component1(): V? = get(true)

internal operator fun <V> Map<Boolean, V>.component2(): V? = get(false)

internal fun ByteArray.hex() = """%032x""".format(BigInteger(this))

internal fun ByteArray.md5(): ByteArray = MessageDigest.getInstance("md5").digest(this)

internal val Url.filename get() = encodedPath.substringAfterLast('/')

internal fun IllustInfo.getContent() = buildMessageChain {
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

internal fun IllustInfo.getPixivCat() = buildMessageChain {
    appendLine("原图连接: ")
    getPixivCatUrls().forEach {
        appendLine(it.toString())
    }
}

internal fun SearchResult.getContent() = buildMessageChain {
    appendLine("相似度: ${similarity * 100}%")
    appendLine("作者: $name ")
    appendLine("UID: $uid ")
    appendLine("标题: $title ")
    appendLine("PID: $pid ")
}

internal suspend fun SpotlightArticle.getContent(contact: Contact) = buildMessageChain {
    appendLine("AID: $aid")
    appendLine("标题: $title")
    appendLine("发布: $publish")
    appendLine("类别: $category")
    appendLine("类型: $type")
    appendLine("链接: $url")
    append(getThumbnailImage().uploadAsImage(contact))
}

internal suspend fun PixivHelper.buildMessageByArticle(data: SpotlightArticleData) = buildMessageChain {
    appendLine("共 ${data.articles.size} 个特辑")
    data.articles.forEach {
        appendLine("================")
        append(it.getContent(contact))
    }
}

internal suspend fun PixivHelper.buildMessageByIllust(illust: IllustInfo, flush: Boolean) = buildMessageChain {
    add(illust.getContent())
    if (link) {
        add(illust.getPixivCat())
    }
    val files = illust.getImages()
    if (illust.age == AgeLimit.ALL) {
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
        illust.save()
    }
}

internal suspend fun PixivHelper.buildMessageByIllust(pid: Long, flush: Boolean) = buildMessageByIllust(
    illust = getIllustInfo(pid = pid, flush = flush),
    flush = flush
)

internal const val NO_PROFILE_IMAGE = "https://s.pximg.net/common/images/no_profile.png"

internal suspend fun PixivHelper.buildMessageByUser(user: UserDetail, save: Boolean): MessageChain = buildMessageChain {
    appendLine("NAME: ${user.user.name}")
    appendLine("UID: ${user.user.id}")
    appendLine("ACCOUNT: ${user.user.account}")
    appendLine("TOTAL: ${user.total()}")
    appendLine("TWITTER: ${user.profile.twitterAccount}")
    runCatching {
        append(user.user.getProfileImage().uploadAsImage(contact))
    }.onFailure {
        logger.warning({ "User(${user.user.id}) ProfileImage 下载失败" }, it)
    }
    if (save) {
        user.save()
    }
}

internal suspend fun PixivHelper.buildMessageByUser(uid: Long, save: Boolean): MessageChain = buildMessageByUser(
    user = userDetail(uid = uid),
    save = save
)

internal fun IllustInfo.getPixivCatUrls() = getOriginImageUrls().map { it.copy(host = PixivMirrorHost) }

internal fun IllustInfo.isEro(): Boolean =
    totalBookmarks ?: 0 >= PixivHelperSettings.eroBookmarks && pageCount <= PixivHelperSettings.eroPageCount && type == WorkContentType.ILLUST

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
    type = type.ordinal,
    width = width,
    height = height,
    totalBookmarks = totalBookmarks ?: 0,
    totalComments = totalComments ?: 0,
    totalView = totalView ?: 0,
    age = age.ordinal,
    isEro = isEro(),
    deleted = false
)

internal fun IllustInfo.getTagInfo() = tags.map {
    TagBaseInfo(
        pid = pid,
        name = it.name,
        translatedName = it.translatedName
    )
}

internal fun UserInfoMapper.add(user: UserBaseInfo) =
    if (findByUid(user.uid) != null) updateUser(user) else replaceUser(user)

internal fun IllustInfo.save(): Unit = useMappers { mappers ->
    mappers.user.add(user.toUserBaseInfo())
    mappers.artwork.replaceArtWork(getArtWorkInfo())
    if (tags.isNotEmpty()) {
        mappers.tag.replaceTags(getTagInfo())
    }
    logger.info { "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${pageCount}]{${totalBookmarks}}信息已设置" }
}

internal fun Collection<IllustInfo>.update(): Unit = useMappers { mappers ->
    logger.verbose { "作品(${first().pid..last().pid})[${size}]信息即将更新" }

    forEach { info ->
        mappers.artwork.updateArtWork(info.getArtWorkInfo())
        if (info.tags.isNotEmpty()) {
            mappers.tag.replaceTags(info.getTagInfo())
        }
    }

    logger.info { "作品{${first().pid..last().pid}}[${size}]信息已更新" }
}

internal fun Collection<IllustInfo>.save(): Unit = useMappers { mappers ->
    logger.verbose { "作品(${first().pid..last().pid})[${size}]信息即将插入" }

    forEach { info ->
        mappers.user.add(info.user.toUserBaseInfo())
        mappers.artwork.replaceArtWork(info.getArtWorkInfo())
        if (info.tags.isNotEmpty()) {
            mappers.tag.replaceTags(info.getTagInfo())
        }
    }

    logger.info { "作品{${first().pid..last().pid}}[${size}]信息已插入" }
}

internal fun UserDetail.save(): Unit = useMappers { mapper -> mapper.user.add(user.toUserBaseInfo()) }

private val Json_ = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
    allowStructuredMapKeys = true
}

private fun folder(pid: Long) = PixivHelperSettings.imagesFolder(pid)

private fun json(pid: Long) = folder(pid).resolve("${pid}.json")

internal fun File.readIllustInfo(): IllustInfo = Json_.decodeFromString(IllustInfo.serializer(), readText())

internal fun IllustInfo.write(file: File = json(pid)) =
    apply { file.apply { parentFile.mkdirs() }.writeText(Json_.encodeToString(IllustInfo.serializer(), this)) }

internal fun List<IllustInfo>.write() = onEach { it.write() }

internal suspend fun PixivHelper.getIllustInfo(
    pid: Long,
    flush: Boolean,
    block: suspend PixivHelper.(Long) -> IllustInfo = {
        illustDetail(it).illust.apply {
            check(user.id != 0L) { "作品已删除或者被限制, Redirect: ${getOriginImageUrls().single()}" }
        }
    },
): IllustInfo = json(pid).let { file ->
    if (!flush && file.exists()) {
        file.readIllustInfo()
    } else {
        block(pid).write(file = file)
    }
}

internal fun Throwable.isNotCancellationException() =
    (this is CancellationException || message == "No more continuations to resume").not()

internal suspend fun UserInfo.getProfileImage(): File {
    val image = Url(profileImageUrls.values.lastOrNull() ?: NO_PROFILE_IMAGE)
    val dir = PixivHelperSettings.profilesFolder
    return dir.resolve(image.filename).apply {
        if (exists().not()) {
            writeBytes(PixivHelperDownloader.downloadImage(url = image))
            logger.info { "用户 $image 下载完成" }
        }
    }
}

internal suspend fun IllustInfo.getImages(): List<File> {
    val dir = folder(pid).apply { mkdirs() }
    val temp = PixivHelperSettings.tempFolder
    val downloads = mutableListOf<Url>()
    val files = getOriginImageUrls().map { url ->
        dir.resolve(url.filename).apply {
            if (exists().not() && temp.resolve(name).exists()) {
                logger.info { "从[${temp.resolve(name)}]移动文件" }
                temp.resolve(name).renameTo(this)
            }
            if (exists().not()) {
                downloads.add(url)
            }
        }
    }
    fun FileInfo(url: Url, bytes: ByteArray) = FileInfo(
        pid = pid,
        index = getOriginImageUrls().indexOf(url),
        md5 = bytes.md5().hex(),
        url = url.toString(),
        size = bytes.size
    )
    if (downloads.isNotEmpty()) {
        val results = PixivHelperDownloader.downloadImageUrls(urls = downloads) { url, result ->
            result.mapCatching {
                dir.resolve(url.filename).writeBytes(it)
                FileInfo(url = url, bytes = it)
            }.onFailure {
                if (it.isNotCancellationException()) {
                    logger.warning({ "[$url]下载失败" }, it)
                }
            }
        }
        results.mapNotNull { it.getOrNull() }.takeIf { it.isNotEmpty() }?.let { list ->
            useMappers { mappers -> mappers.file.replaceFiles(list = list) }
        }
        check(results.all { it.isSuccess }) { "作品(${pid})下载失败" }
        logger.info { "作品(${pid})<${createAt}>[${type}][${user.id}][${title}][${downloads.size}]{${totalBookmarks}}下载完成" }
    }
    return files
}

internal suspend fun SpotlightArticle.getThumbnailImage(): File {
    val image = Url(thumbnail)
    val temp = PixivHelperSettings.articlesFolder
    return temp.resolve(image.filename).apply {
        if (exists().not()) {
            writeBytes(PixivHelperDownloader.downloadImage(url = image))
            logger.info { "特辑封面 $image 下载完成" }
        }
    }
}

internal fun getBackupList() = buildMap<String, File> {
    this["DATA"] = PixivHelperPlugin.dataFolder
    this["CONFIG"] = PixivHelperPlugin.configFolder
    this["DATABASE"] = PixivHelperSettings.sqlite
}

internal fun MessageSource.inDuration(duration: Duration) =
    time > OffsetDateTime.now().toEpochSecond() - duration.inSeconds.toLong()