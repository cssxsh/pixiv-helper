package xyz.cssxsh.mirai.plugin

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import okio.ByteString.Companion.toByteString
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.exception.*
import java.io.*
import java.lang.*

internal val logger by lazy {
    val open = System.getProperty("xyz.cssxsh.mirai.plugin.logger", "${true}").toBoolean()
    if (open) PixivHelperPlugin.logger else SilentLogger
}

val SendLimit = """本群每分钟只能发\d+条消息""".toRegex()

const val SendDelay = 60 * 1000L

suspend fun <T : CommandSenderOnMessage<*>> T.sendMessage(block: suspend T.(Contact) -> Message): Boolean {
    return runCatching {
        block(fromEvent.subject)
    }.onSuccess { message ->
        quoteReply(message)
    }.onFailure {
        when {
            SendLimit in it.message.orEmpty() -> {
                delay(SendDelay)
                quoteReply(SendLimit.find(it.message!!)!!.value)
            }
            else -> {
                quoteReply("发送消息失败， ${it.message}")
            }
        }
    }.isSuccess
}

suspend fun CommandSenderOnMessage<*>.quoteReply(message: Message) = sendMessage(fromEvent.message.quote() + message)

suspend fun CommandSenderOnMessage<*>.quoteReply(message: String) = quoteReply(message.toPlainText())

internal suspend fun CommandSenderOnMessage<*>.withHelper(block: suspend PixivHelper.() -> Any?): Boolean {
    return runCatching {
        helper.block()
    }.onSuccess { message ->
        when (message) {
            null, Unit -> Unit
            is Message -> quoteReply(message)
            is String -> quoteReply(message)
            is IllustInfo -> sendIllust(message)
            is ArtWorkInfo -> sendArtwork(message)
            else -> quoteReply(message.toString())
        }
    }.onFailure {
        logger.warning({ "消息回复失败" }, it)
        when {
            it is AppApiException -> {
                quoteReply("ApiException, ${it.message}")
            }
            SendLimit in it.message.orEmpty() -> {
                delay(60 * 1000L)
                quoteReply(SendLimit.find(it.message!!)!!.value)
            }
            else -> {
                quoteReply(it.toString())
            }
        }
    }.isSuccess
}

internal suspend fun CommandSenderOnMessage<*>.sendIllust(illust: IllustInfo): Boolean {
    return runCatching {
        helper.buildMessageByIllust(illust = illust)
    }.mapCatching { message ->
        withTimeout(3 * 60 * 1000L) {
            when (val model = helper.model) {
                is SendModel.Normal -> quoteReply(message)
                is SendModel.Flash -> {
                    val images = message.filterIsInstance<Image>()
                    quoteReply((message - images).toMessageChain())
                    images.forEach { quoteReply(it.flash()) }
                }
                is SendModel.Recall -> {
                    quoteReply(message)?.recallIn(model.ms)
                }
            }
        }
    }.onFailure {
        when {
            SendLimit in it.message.orEmpty() -> {
                delay(60 * 1000L)
                quoteReply(SendLimit.find(it.message!!)!!.value)
            }
            else -> {
                logger.warning({ "读取色图失败" }, it)
                quoteReply("读取色图失败, ${it.message}")
            }
        }
    }.isSuccess
}

internal suspend fun CommandSenderOnMessage<*>.sendArtwork(info: ArtWorkInfo): Boolean {
    return sendIllust(helper.getIllustInfo(pid = info.pid, flush = false))
}

/**
 * 通过正负号区分群和用户
 */
val Contact.delegate get() = if (this is Group) id * -1 else id

/**
 * 查找Contact
 */
fun findContact(delegate: Long): Contact? {
    Bot.instances.forEach { bot ->
        if (delegate < 0) {
            bot.getGroup(delegate * -1)?.let { return@findContact it }
        } else {
            bot.getFriend(delegate)?.let { return@findContact it }
            bot.getStranger(delegate)?.let { return@findContact it }
            bot.groups.forEach { group ->
                group.getMember(delegate)?.let { return@findContact it }
            }
        }
    }
    return null
}

/**
 * 连续发送间隔时间
 */
internal val SendInterval by PixivConfigData::interval

/**
 * 涩图防重复间隔
 */
internal val EroChunk by PixivHelperSettings::eroChunk

/**
 * 涩图提高收藏数时间
 */
internal val EroUpExpire by PixivHelperSettings::eroUpExpire

/**
 * Tag指令冷却时间
 */
internal val TagCooling by PixivHelperSettings::tagCooling

internal val TagAgeLimit get() = if (PixivHelperSettings.tagSFW) AgeLimit.ALL else AgeLimit.R18G

internal val EroAgeLimit get() = if (PixivHelperSettings.eroSFW) AgeLimit.ALL else AgeLimit.R18G

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
    appendLine("<=============>")
    appendLine("相似度: ${similarity * 100}%")
    when (this@getContent) {
        is PixivSearchResult -> {
            if (similarity > MIN_SIMILARITY) associate()
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

internal fun List<SearchResult>.getContent() = map { it.getContent() }.toMessageChain().ifEmpty { "结果为空".toPlainText() }

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
    val files = if (illust.type != WorkContentType.UGOIRA) illust.getImages() else listOf(getUgoira(illust))
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
    preview.illusts.apply { replicate() }.write().filter { it.isEro() }.forEach { illust ->
        runCatching {
            val files = if (illust.type != WorkContentType.UGOIRA) illust.getImages() else listOf(getUgoira(illust))
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

private fun IllustInfo.bookmarks(min: Long): Boolean = (totalBookmarks ?: 0) > min

private fun IllustInfo.pages(max: Int): Boolean = pageCount < max

private fun IllustInfo.match(tag: Regex): Boolean = tags.any { tag in it.name || tag in it.translatedName.orEmpty() }

private fun IllustInfo.user(ids: Set<Long>): Boolean = user.id in ids

internal fun IllustInfo.isEro(mark: Boolean = true): Boolean = PixivHelperSettings.let { ero: EroStandardConfig ->
    (ero.types.isEmpty() || type in ero.types) &&
        (mark.not() || bookmarks(ero.bookmarks)) &&
        (pages(ero.pages)) &&
        (match(ero.tagExclude).not()) &&
        (user(ero.userExclude).not())
}

internal fun IllustInfo.check() = apply {
    check(user.id != 0L) { "[$pid] 作品已删除或者被限制, Redirect: ${getOriginImageUrls().single()}" }
}

private fun folder(pid: Long) = PixivHelperSettings.imagesFolder(pid)

private fun json(pid: Long) = folder(pid).resolve("${pid}.json")

internal fun File.readIllustInfo(): IllustInfo = PixivJson.decodeFromString(IllustInfo.serializer(), readText())

internal fun IllustInfo.write(file: File = json(pid)) {
    file.parentFile.mkdirs()
    file.writeText(PixivJson.encodeToString(IllustInfo.serializer(), this))
}

internal fun Collection<IllustInfo>.write() = onEach { it.write() }

private val FlushIllustInfo: suspend PixivHelper.(Long) -> IllustInfo = { pid ->
    illustDetail(pid).illust.check().apply { replicate() }
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

internal fun Throwable.isCancellationException() =
    (this is CancellationException || message == "No more continuations to resume")

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
    val urls = getOriginImageUrls().filter { "$pid" in it.encodedPath }
    val files = urls.map { url ->
        dir.resolve(url.filename).apply {
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
        var size = 0L

        downloads.removeIf { url ->
            val file = temp.resolve(url.filename)
            val exists = file.exists()
            if (exists) {
                logger.info { "从[${file}]移动文件" }
                results.add(FileInfo(url = url, bytes = file.readBytes()))
                file.renameTo(dir.resolve(url.filename))
            }
            exists
        }

        PixivHelperDownloader.downloadImageUrls(urls = downloads) { url, deferred ->
            runCatching {
                val bytes = deferred.await()
                temp.resolve(url.filename).writeBytes(bytes)
                size += bytes.size
                FileInfo(url = url, bytes = bytes)
            }.onFailure {
                logger.warning({ "[$url]下载失败" }, it)
            }.onSuccess {
                results.add(it)
            }
        }

        if (pid !in ArtWorkInfo) this.replicate()
        results.replicate()

        logger.info {
            "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${size.toBytesSize()}]{${totalBookmarks}}下载完成"
        }

        downloads.forEach { url ->
            temp.resolve(url.filename).apply {
                if (exists()) renameTo(dir.resolve(url.filename))
            }
        }
    }
    return files
}

internal suspend fun PixivHelper.getUgoira(illust: IllustInfo, flush: Boolean = false): File {
    return PixivHelperGifEncoder.build(illust, ugoiraMetadata(illust.pid).ugoira, flush)
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
    return mutableMapOf<String, File>().apply {
        if (PixivHelperPlugin.dataFolder.list().isNullOrEmpty().not()) {
            put("DATA", PixivHelperPlugin.dataFolder)
        }
        if (PixivHelperPlugin.configFolder.list().isNullOrEmpty().not()) {
            put("CONFIG", PixivHelperPlugin.configFolder)
        }
        if (SqlMetaData.url.startsWith("jdbc:sqlite:")) {
            put("DATABASE", File(SqlMetaData.url.removePrefix("jdbc:sqlite:")))
        }
    }
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

internal fun MessageSource.key() = ids.asList() + time