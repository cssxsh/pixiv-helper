package xyz.cssxsh.mirai.plugin

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import okio.ByteString.Companion.toByteString
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import java.io.File
import java.lang.*

internal val logger by PixivHelperPlugin::logger

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
        logger.warning({ "消息回复失败" }, it)
        when {
            SendLimit.containsMatchIn(it.message.orEmpty()) -> {
                delay(60 * 1000L)
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
    }.mapCatching {
        withTimeout(3 * 60 * 1000L) {
            quoteReply(it)
        }
    }.onFailure {
        when {
            SendLimit.containsMatchIn(it.message.orEmpty()) -> {
                delay(60 * 1000L)
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
) = sendIllust(flush = false) { getIllustInfo(pid = block().pid, flush = false) }

/**
 * 连续发送间隔时间
 */
internal val SendInterval by PixivConfigData::interval

/**
 * 涩图防重复间隔
 */
internal val EroInterval by PixivHelperSettings::eroInterval

/**
 * 涩图提高收藏数时间
 */
internal val EroUpExpire by PixivHelperSettings::eroUpExpire

internal operator fun <V> Map<Boolean, V>.component1(): V? = get(true)

internal operator fun <V> Map<Boolean, V>.component2(): V? = get(false)

internal val Url.filename get() = encodedPath.substringAfterLast('/')

internal fun IllustInfo.getContent(link: Boolean, tag: Boolean, attr: Boolean) = buildMessageChain {
    appendLine("作者: ${user.name} ")
    appendLine("UID: ${user.id} ")
    if (attr) appendLine("已关注: ${user.isFollowed ?: false}")
    appendLine("标题: $title ")
    appendLine("PID: $pid ")
    if (attr) appendLine("已收藏: $isBookmarked")
    if (attr) appendLine("收藏数: $totalBookmarks ")
    if (attr) appendLine("SAN值: $sanityLevel ")
    if (attr) appendLine("创作于: $createAt ")
    if (attr) appendLine("共: $pageCount 张图片 ")
    if (tag) appendLine("标签：${tags.map { it.getContent() }}")
    if (link) add(getPixivCat())
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
    when (this@getContent) {
        is PixivSearchResult -> {
            appendLine("作者: $name ")
            appendLine("UID: $uid ")
            appendLine("标题: $title ")
            appendLine("PID: $pid ")
        }
        is TwitterSearchResult -> {
            appendLine("Tweet: $tweet")
            appendLine("原图: $image")
        }
        is OtherSearchResult -> {
            appendLine(text)
        }
    }
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
    add(illust.getContent(link, tag, attr))
    val files = illust.getImages()
    if (illust.age == AgeLimit.ALL) {
        if (files.size <= max) {
            files
        } else {
            logger.warning { "[${illust.pid}](${files.size})图片过多" }
            add("部分图片省略".toPlainText())
            files.subList(0, max)
        }.map { file ->
            add(runCatching {
                file.uploadAsImage(contact)
            }.getOrElse {
                "上传失败, $it".toPlainText()
            })
        }
    } else {
        add("R18禁止！".toPlainText())
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
            illust.saveOrUpdate()
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

internal fun IllustInfo.isEro(): Boolean {
    val ero: EroStandardConfig = PixivHelperSettings
    if (ero.types.isNotEmpty() && type !in ero.types) return false
    if (totalBookmarks ?: 0 <= ero.bookmarks) return false
    if (pageCount > ero.pages) return false
    if (tags.any { ero.tagExclude in it.name || ero.tagExclude in it.translatedName.orEmpty() }) return false
    if (user.id in ero.userExclude) return true
    return true
}

internal fun IllustInfo.check() = apply {
    check(user.id != 0L) { "作品已删除或者被限制, Redirect: ${getOriginImageUrls().single()}" }
}

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

private val FlushIllustInfo: suspend PixivHelper.(Long) -> IllustInfo = { pid ->
    illustDetail(pid).illust.check().apply { saveOrUpdate() }
}

internal suspend fun PixivHelper.getIllustInfo(
    pid: Long,
    flush: Boolean,
    block: suspend PixivHelper.(Long) -> IllustInfo = FlushIllustInfo,
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
            writeBytes(PixivHelperDownloader.download(url = image))
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
        val results = mutableListOf<FileInfo>()
        PixivHelperDownloader.downloadImageUrls(urls = downloads) { url, result ->
            runCatching {
                val bytes = result.getOrThrow()
                dir.resolve(url.filename).writeBytes(bytes)
                FileInfo(url = url, bytes = bytes)
            }.onFailure {
                logger.warning({ "[$url]下载失败" }, it)
            }.onSuccess {
                results.add(it)
            }
        }
        results.saveOrUpdate()
        val size = files.sumOf { it.length() }.toBytesSize()
        logger.info {
            "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${size}]{${totalBookmarks}}下载完成"
        }
    }
    return files
}

internal suspend fun SpotlightArticle.getThumbnailImage(): File {
    val image = Url(thumbnail)
    val temp = PixivHelperSettings.articlesFolder
    return temp.resolve(image.filename).apply {
        if (exists().not()) {
            writeBytes(PixivHelperDownloader.download(url = image))
            logger.info { "特辑封面 $image 下载完成" }
        }
    }
}

internal val bit: (Int) -> Long = { 1L shl it }

internal fun Long.toBytesSize() = when (this) {
    0L -> "0"
    in bit(0) until bit(10) -> "%dB".format(this)
    in bit(10) until bit(20) -> "%dKB".format(this / bit(10))
    in bit(20) until bit(30) -> "%dMB".format(this / bit(20))
    in bit(20) until bit(30) -> "%dGB".format(this / bit(20))
    else -> throw IllegalStateException("Too Big")
}

internal fun getBackupList(): Map<String, File> {
    return mapOf("DATA" to PixivHelperPlugin.dataFolder, "CONFIG" to PixivHelperPlugin.configFolder)
}

internal suspend fun PixivHelper.redirect(account: String): Long {
    check(account.isNotBlank()) { "Account is Blank" }
    UserBaseInfo.account(account)?.let { return@redirect it.uid }
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