package xyz.cssxsh.mirai.plugin

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import okio.ByteString.Companion.toByteString
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.dao.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.io.File
import kotlin.time.*

internal val logger get() = PixivHelperPlugin.logger

private val SendLimit = """本群每分钟只能发\d+条消息""".toRegex()

internal suspend fun CommandSenderOnMessage<*>.withHelper(block: suspend PixivHelper.() -> Any?): Boolean {
    return runCatching {
        helper.block()
    }.onSuccess { message ->
        when (message) {
            null, Unit -> Unit
            is Message -> quoteReply(message)
            is String -> quoteReply(message)
            else -> quoteReply(message.toString())
        }
    }.onFailure {
        when {
            SendLimit.containsMatchIn(it.message.orEmpty()) -> {
                delay((1).minutes)
                quoteReply(SendLimit.find(it.message!!)!!.value)
            }
            else -> {
                quoteReply(it.toString())
            }
        }
    }.isSuccess
}

internal suspend fun CommandSenderOnMessage<*>.sendIllust(
    flush: Boolean,
    block: suspend PixivHelper.() -> IllustInfo,
): Boolean {
    return runCatching {
        helper.run {
            buildMessageByIllust(illust = block().also { info ->
                if (flush || json(info.pid).exists().not()) info.write()
            })
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        when {
            SendLimit.containsMatchIn(it.message.orEmpty()) -> {
                delay((1).minutes)
                quoteReply(SendLimit.find(it.message!!)!!.value)
            }
            else -> {
                logger.warning({ "读取色图失败" }, it)
                quoteReply("读取色图失败， ${it.message}")
            }
        }
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
internal val SendInterval get() = PixivConfigData.interval.seconds

suspend fun CommandSenderOnMessage<*>.quoteReply(message: Message) = sendMessage(message + fromEvent.message.quote())

suspend fun CommandSenderOnMessage<*>.quoteReply(message: String) = quoteReply(message.toPlainText())

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

internal fun UserInfo.count() = useMappers { it.artwork.countByUid(id) }

internal fun UserDetail.total() = profile.totalIllusts + profile.totalManga

internal operator fun <V> Map<Boolean, V>.component1(): V? = get(true)

internal operator fun <V> Map<Boolean, V>.component2(): V? = get(false)

internal val Url.filename get() = encodedPath.substringAfterLast('/')

internal fun IllustInfo.getContent() = buildMessageChain {
    appendLine("作者: ${user.name} ")
    appendLine("UID: ${user.id} ")
    appendLine("已关注: ${user.isFollowed ?: false}")
    appendLine("标题: $title ")
    appendLine("PID: $pid ")
    appendLine("已收藏: $isBookmarked")
    appendLine("收藏数: $totalBookmarks ")
    appendLine("SAN值: $sanityLevel ")
    appendLine("创作于: $createAt ")
    appendLine("共: $pageCount 张图片 ")
    appendLine("标签：${tags.map { it.getContent() }}")
}

internal fun TagInfo.getContent() = name + (translatedName?.let { " -> $it" } ?: "")

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

internal suspend fun PixivHelper.buildMessageByIllust(illust: IllustInfo) = buildMessageChain {
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
}

internal const val NO_PROFILE_IMAGE = "https://s.pximg.net/common/images/no_profile.png"

internal suspend fun PixivHelper.buildMessageByUser(preview: UserPreview) = buildMessageChain {
    appendLine("NAME: ${preview.user.name}")
    appendLine("UID: ${preview.user.id}")
    appendLine("ACCOUNT: ${preview.user.account}")
    appendLine("FOLLOWED: ${preview.user.isFollowed}")
    runCatching {
        append(preview.user.getProfileImage().uploadAsImage(contact))
    }.onFailure {
        logger.warning({ "User(${preview.user.id}) ProfileImage 下载失败" }, it)
    }
    preview.illusts.filter { it.isEro() }.forEach { illust ->
        runCatching {
            illust.save()
            val files = illust.getImages()
            if (illust.age == AgeLimit.ALL) {
                add(files.first().uploadAsImage(contact))
            }
        }
    }
}

internal suspend fun PixivHelper.buildMessageByUser(detail: UserDetail) = buildMessageChain {
    appendLine("NAME: ${detail.user.name}")
    appendLine("UID: ${detail.user.id}")
    appendLine("ACCOUNT: ${detail.user.account}")
    appendLine("FOLLOWED: ${detail.user.isFollowed}")
    appendLine("TOTAL: ${detail.total()}")
    appendLine("TWITTER: ${detail.profile.twitterAccount}")
    runCatching {
        append(detail.user.getProfileImage().uploadAsImage(contact))
    }.onFailure {
        logger.warning({ "User(${detail.user.id}) ProfileImage 下载失败" }, it)
    }
}

internal suspend fun PixivHelper.buildMessageByUser(uid: Long) = buildMessageByUser(detail = userDetail(uid = uid))

internal fun IllustInfo.getPixivCatUrls() = getOriginImageUrls().map { it.copy(host = PixivMirrorHost) }

internal fun IllustInfo.isEro(): Boolean =
    totalBookmarks ?: 0 >= PixivHelperSettings.eroBookmarks && pageCount <= PixivHelperSettings.eroPageCount && type == WorkContentType.ILLUST

internal fun UserInfo.toUserBaseInfo() = UserBaseInfo(uid = id, name = name, account = account)

internal fun SimpleArtworkInfo.toUserBaseInfo() = UserBaseInfo(uid = uid, name = name, account = "")

internal fun IllustInfo.toArtWorkInfo() = ArtWorkInfo(
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

internal fun SimpleArtworkInfo.toArtWorkInfo() = EmptyArtWorkInfo.copy(pid = pid, uid = uid, title = title)

internal fun IllustInfo.toTagInfo() = tags.map {
    TagBaseInfo(pid = pid, name = it.name, translatedName = it.translatedName)
}

internal fun UserInfoMapper.add(user: UserBaseInfo) =
    if (findByUid(user.uid) != null) updateUser(user) else replaceUser(user)

internal fun IllustInfo.save(): Unit = useMappers { mappers ->
    mappers.user.add(user.toUserBaseInfo())
    mappers.artwork.replaceArtWork(toArtWorkInfo())
    if (tags.isNotEmpty()) {
        mappers.tag.replaceTags(toTagInfo())
    }
    logger.info { "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${pageCount}]{${totalBookmarks}}信息已设置" }
}

internal fun Collection<IllustInfo>.update(): Unit = useMappers { mappers ->
    logger.verbose { "作品(${first().pid..last().pid})[${size}]信息即将更新" }

    forEach { info ->
        mappers.artwork.updateArtWork(info.toArtWorkInfo())
        if (info.tags.isNotEmpty()) {
            mappers.tag.replaceTags(info.toTagInfo())
        }
    }

    logger.info { "作品{${first().pid..last().pid}}[${size}]信息已更新" }
}

internal fun Collection<IllustInfo>.save(): Unit = useMappers { mappers ->
    logger.verbose { "作品(${first().pid..last().pid})[${size}]信息即将插入" }

    forEach { info ->
        mappers.user.add(info.user.toUserBaseInfo())
        mappers.artwork.replaceArtWork(info.toArtWorkInfo())
        if (info.tags.isNotEmpty()) {
            mappers.tag.replaceTags(info.toTagInfo())
        }
    }

    logger.info { "作品{${first().pid..last().pid}}[${size}]信息已插入" }
}

internal fun UserInfo.save(): Unit = useMappers { it.user.add(toUserBaseInfo()) }

internal fun Image.findSearchResult() = useMappers { it.statistic.findSearchResult(md5.toByteString().hex()) }

internal fun SearchResult.save() = useMappers { it.statistic.replaceSearchResult(this) }

private val Json_ = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
    allowStructuredMapKeys = true
}

private fun folder(pid: Long) = PixivHelperSettings.imagesFolder(pid)

private fun json(pid: Long) = folder(pid).resolve("${pid}.json")

internal fun File.readIllustInfo(): IllustInfo = Json_.decodeFromString(IllustInfo.serializer(), readText())

internal fun IllustInfo.write(file: File = json(pid)) {
    file.parentFile.mkdirs()
    file.writeText(Json_.encodeToString(IllustInfo.serializer(), this))
}

internal fun Collection<IllustInfo>.write() = onEach { it.write() }

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
        runCatching {
            file.readIllustInfo()
        }.onFailure {
            logger.warning({ "文件${file.absolutePath}读取存在问题" }, it)
        }.getOrThrow()
    } else {
        block(pid).apply {
            write(file = file)
        }
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
    val urls = getOriginImageUrls()
    val files = urls.map { url ->
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
        index = urls.indexOf(url),
        md5 = bytes.toByteString().md5().hex(),
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
            if (useMappers { it.artwork.contains(pid).not() }) save()
            useMappers { it.file.replaceFiles(list) }
        }
        check(results.all { it.isSuccess }) { "作品(${pid})下载失败" }
        logger.info { "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${downloads.size}]{${totalBookmarks}}下载完成" }
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

internal suspend fun PixivHelper.redirect(account: String): Long {
    useMappers { it.user.findByAccount(account = account) }?.let { return@redirect it.uid }
    val url = Url("https://pixiv.me/$account")
    return useHttpClient { client ->
        client.head<HttpResponse>(url).request.url
    }.let { location ->
        requireNotNull(URL_USER_REGEX.find(location.encodedPath)) {
            "跳转失败, $url -> $location"
        }
    }.value.toLong()
}

internal data class MessageSourceMetadata(
    val ids: List<Int>,
    val internalIds: List<Int>,
    val time: Int,
)

internal fun MessageSource.metadata() = MessageSourceMetadata(
    ids = ids.toList(),
    internalIds = internalIds.toList(),
    time = time
)