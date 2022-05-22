package xyz.cssxsh.mirai.pixiv

import io.ktor.http.*
import kotlinx.coroutines.*
import net.mamoe.mirai.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import okio.ByteString.Companion.toByteString
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.exception.*
import xyz.cssxsh.pixiv.fanbox.*
import xyz.cssxsh.pixiv.web.*
import java.io.*

// region Send Message

val SendLimit = """本群每分钟只能发\d+条消息""".toRegex()

suspend fun UserCommandSender.quoteReply(message: Message): MessageReceipt<Contact>? {
    return if (this is CommandSenderOnMessage<*>) {
        sendMessage(fromEvent.message.quote() + message)
    } else {
        sendMessage(At(user) + message)
    }
}

suspend fun UserCommandSender.quoteReply(message: String) = quoteReply(message.toPlainText())

internal suspend fun UserCommandSender.withHelper(block: suspend PixivHelper.() -> Any?): Boolean {
    return try {
        when (val message = helper.block()) {
            null, Unit -> Unit
            is ForwardMessage -> {
                check(message.nodeList.size <= 200) {
                    throw MessageTooLargeException(
                        subject, message, message,
                        "ForwardMessage allows up to 200 nodes, but found ${message.nodeList.size}"
                    )
                }
                @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
                sendMessage(message + net.mamoe.mirai.internal.message.IgnoreLengthCheck)
            }
            is Message -> quoteReply(message)
            is String -> quoteReply(message)
            is IllustInfo -> sendIllust(message)
            is ArtWorkInfo -> sendArtwork(message)
            else -> quoteReply(message.toString())
        }
        true
    } catch (cause: Throwable) {
        logger.warning({ "消息回复失败" }, cause)
        when {
            cause is AppApiException -> {
                quoteReply("ApiException, ${cause.json}")
            }
            cause is RestrictException -> {
                if (cause.illust.pid in ArtWorkInfo) {
                    ArtWorkInfo.delete(pid = cause.illust.pid, comment = cause.message)
                } else {
                    cause.illust.toArtWorkInfo().copy(caption = cause.message).replicate()
                }
                quoteReply("RestrictException, ${cause.illust}")
            }
            cause is TimeoutCancellationException -> {
                quoteReply(cause.message!!)
            }
            SendLimit in cause.message.orEmpty() -> {
                delay(60 * 1000L)
                quoteReply(SendLimit.find(cause.message!!)!!.value)
            }
            else -> {
                quoteReply(cause.toString())
            }
        }
        false
    }
}

internal suspend fun CommandSenderOnMessage<*>.withHelper(block: suspend PixivHelper.() -> Any?): Boolean {
    return (this as UserCommandSender).withHelper(block)
}

internal suspend fun UserCommandSender.sendIllust(illust: IllustInfo): Boolean {
    return try {
        val message = helper.buildMessageByIllust(illust = illust)
        withTimeout(3 * 60 * 1000L) {
            when (val model = helper.model) {
                is SendModel.Normal -> quoteReply(message)
                is SendModel.Flash -> {
                    val images = message.filterIsInstance<Image>()
                    quoteReply(message.firstIsInstance<PlainText>())
                    for (image in images) quoteReply(image.flash())
                }
                is SendModel.Recall -> {
                    quoteReply(message)?.recallIn(model.ms)
                }
                is SendModel.Forward -> {
                    sendMessage(
                        message.toForwardMessage(
                            sender = user,
                            displayStrategy = illust.toDisplayStrategy()
                        )
                    )
                }
            }
        }
        true
    } catch (cause: Throwable) {
        logger.warning({ "消息作品失败" }, cause)
        when {
            cause is AppApiException -> {
                quoteReply("ApiException, ${cause.json}")
            }
            cause is RestrictException -> {
                if (cause.illust.pid in ArtWorkInfo) {
                    ArtWorkInfo.delete(pid = cause.illust.pid, comment = cause.message)
                } else {
                    cause.illust.toArtWorkInfo().copy(caption = cause.message).replicate()
                }
                quoteReply("RestrictException, ${cause.illust}")
            }
            SendLimit in cause.message.orEmpty() -> {
                delay(60 * 1000L)
                quoteReply(SendLimit.find(cause.message!!)!!.value)
            }
            else -> {
                logger.warning({ "读取色图失败" }, cause)
                quoteReply("读取色图失败, ${cause.message}")
            }
        }
        false
    }
}

internal suspend fun UserCommandSender.sendArtwork(info: ArtWorkInfo): Boolean {
    return sendIllust(illust = helper.getIllustInfo(pid = info.pid, flush = false))
}

/**
 * 通过正负号区分群和用户
 */
val Contact.delegate get() = if (this is Group) id * -1 else id

/**
 * 查找Contact
 */
fun findContact(delegate: Long): Contact? {
    for (bot in Bot.instances) {
        if (delegate < 0) {
            for (group in bot.groups) {
                if (group.id == delegate * -1) return group
            }
        } else {
            for (friend in bot.friends) {
                if (friend.id == delegate) return friend
            }
            for (stranger in bot.strangers) {
                if (stranger.id == delegate) return stranger
            }
            for (group in bot.groups) {
                for (member in group.members) {
                    if (member.id == delegate) return member
                }
            }
        }
    }
    return null
}

// endregion

// region Build Message

internal class DisplayStrategyBuilder {
    var title: String? = null
    var brief: String? = null
    var source: String? = null
    var preview: List<String>? = null
    var summary: String? = null

    fun build() = object : ForwardMessage.DisplayStrategy {

        override fun generateTitle(forward: RawForwardMessage): String =
            title ?: ForwardMessage.DisplayStrategy.Default.generateTitle(forward)

        override fun generateBrief(forward: RawForwardMessage): String =
            brief ?: ForwardMessage.DisplayStrategy.Default.generateBrief(forward)

        override fun generateSource(forward: RawForwardMessage): String =
            source ?: ForwardMessage.DisplayStrategy.Default.generateSource(forward)

        override fun generatePreview(forward: RawForwardMessage): List<String> =
            preview ?: ForwardMessage.DisplayStrategy.Default.generatePreview(forward)

        override fun generateSummary(forward: RawForwardMessage): String =
            summary ?: ForwardMessage.DisplayStrategy.Default.generateSummary(forward)
    }
}

internal fun RawForwardMessage.render(display: DisplayStrategyBuilder.() -> Unit): ForwardMessage {
    return render(DisplayStrategyBuilder().apply(display).build())
}

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
    if (link) add(getMirror())
}

internal fun TagInfo.getContent() = name + (translatedName?.let { " -> $it" } ?: "")

internal fun IllustInfo.getMirror() = buildMessageChain {
    appendLine("原图连接: ")
    for (url in getMirrorUrls()) {
        appendLine(url.toString())
    }
}

internal fun IllustInfo.toDisplayStrategy() = object : ForwardMessage.DisplayStrategy {

    override fun generateTitle(forward: RawForwardMessage): String = "${user.name} - $title"

    override fun generatePreview(forward: RawForwardMessage): List<String> = tags.map { it.getContent() }

    override fun generateSummary(forward: RawForwardMessage): String = "查看${user.name}的作品"
}

internal suspend fun SearchResult.getContent(contact: Contact) = buildMessageChain {
    appendLine("相似度: ${similarity * 100}%")
    when (this@getContent) {
        is PixivSearchResult -> {
            if (similarity > MIN_SIMILARITY) associate()
            appendLine("作者: $name ")
            appendLine("UID: $uid ")
            appendLine("标题: $title ")
            appendLine("PID: $pid ")

            try {
                val info = ArtWorkInfo[pid]
                if (info != null && info.age == AgeLimit.ALL.ordinal) {
                    with(contact.helper) {
                        withTimeout(30_000L) {
                            getIllustInfo(pid = info.pid, flush = false)
                                .useImageResources { _, resource -> add(resource.uploadAsImage(contact)) }
                        }
                    }
                }
            } catch (_: Throwable) {
                // ignore
            }
        }
        is TwitterSearchResult -> {
            val screen = URL_TWITTER_SCREEN.find(tweet)?.value.orEmpty()
            Twitter[screen]?.let { (_, uid) ->
                appendLine("PIXIV_UID: $uid")
            }
            appendLine("Tweet: $tweet")
            appendLine("原图: $image")
        }
        is OtherSearchResult -> {
            appendLine(text)
        }
    }
}

internal suspend fun List<SearchResult>.getContent(sender: User): Message {
    if (isEmpty()) return "结果为空".toPlainText()
    val contact = (sender as? Member)?.group ?: sender

    return if (ImageSearchConfig.forward) {
        RawForwardMessage(map { result ->
            ForwardMessage.Node(
                senderId = sender.id,
                time = (System.currentTimeMillis() / 1000).toInt(),
                senderName = sender.nameCardOrNick,
                messageChain = result.getContent(contact)
            )
        }).render {
            title = "搜图结果"
            summary = "查看${size}条搜图结果"
        }
    } else {
        map { "<=============>\n".toPlainText() + it.getContent(contact) }.toMessageChain()
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

internal suspend fun PixivHelper.buildMessageByArticle(articles: List<SpotlightArticle>) = buildMessageChain {
    appendLine("共 ${articles.size} 个特辑")
    for (article in articles) {
        appendLine("================")
        append(article.getContent(contact))
    }
}

internal suspend fun PixivHelper.buildMessageByIllust(illust: IllustInfo) = buildMessageChain {
    add(illust.getContent(link, tag, attr))
    if (illust.age == AgeLimit.ALL) {
        illust.useImageResources { index, resource ->
            when {
                index == max -> {
                    logger.warning { "[${illust.pid}](${illust.pageCount})图片过多" }
                    add("部分图片省略\n".toPlainText())
                }
                index < max -> {
                    add(
                        try {
                            resource.uploadAsImage(contact)
                        } catch (cause: Throwable) {
                            "${(resource.origin as? File)?.name}上传失败, $cause\n".toPlainText()
                        }
                    )
                }
                else -> Unit
            }
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
    appendLine("TWITTER: ${Twitter[preview.user.id].joinToString { it.screen }}")
    try {
        append(preview.user.getProfileImage().uploadAsImage(contact))
    } catch (e: Throwable) {
        logger.warning({ "User(${preview.user.id}) ProfileImage 下载失败" }, e)
    }
    for (illust in preview.illusts.apply { replicate() }.write()) {
        if (illust.isEro().not()) continue
        try {
            if (illust.age == AgeLimit.ALL) {
                illust.useImageResources { index, resource ->
                    if (index < 1) add(resource.uploadAsImage(contact))
                }
            }
        } catch (e: Throwable) {
            logger.warning({ "User(${preview.user.id}) PreviewImage 下载失败" }, e)
        }
    }
}

internal suspend fun PixivHelper.buildMessageByUser(detail: UserDetail) = buildMessageChain {
    appendLine("NAME: ${detail.user.name}")
    appendLine("UID: ${detail.user.id}")
    appendLine("ACCOUNT: ${detail.user.account}")
    appendLine("FOLLOWED: ${detail.user.isFollowed}")
    appendLine("TOTAL: ${detail.profile.totalArtwork}")
    appendLine("TWITTER: ${detail.twitter()}")
    try {
        append(detail.user.getProfileImage().uploadAsImage(contact))
    } catch (e: Throwable) {
        logger.warning({ "User(${detail.user.id}) ProfileImage 下载失败" }, e)
    }
}

internal suspend fun PixivHelper.buildMessageByUser(uid: Long) = buildMessageByUser(detail = userDetail(uid))

internal suspend fun PixivHelper.buildMessageByCreator(creator: CreatorDetail) = buildMessageChain {
    appendLine("NAME: ${creator.user.name}")
    appendLine("UID: ${creator.user.userId}")
    appendLine("CREATOR_ID: ${creator.creatorId}")
    appendLine("HAS_ADULT_CONTENT: ${creator.hasAdultContent}")
    appendLine("TWITTER: ${creator.twitter()}")
    appendLine(creator.description)
    try {
        append(creator.getCoverImage()?.uploadAsImage(contact) ?: "".toPlainText())
    } catch (e: Throwable) {
        logger.warning({ "Creator(${creator.creatorId}) CoverImage 下载失败" }, e)
    }
}

internal fun IllustInfo.getMirrorUrls(): List<Url> {
    return getOriginImageUrls().map { it.copy(host = ProxyMirror.ifBlank { PixivMirrorHost }) }
}

private fun IllustInfo.bookmarks(min: Long): Boolean = (totalBookmarks ?: 0) > min

private fun IllustInfo.pages(max: Int): Boolean = pageCount < max

private fun IllustInfo.match(tag: Regex): Boolean = tags.any { tag in it.name || tag in it.translatedName.orEmpty() }

private fun IllustInfo.user(ids: Set<Long>): Boolean = user.id in ids

internal fun IllustInfo.isEro(mark: Boolean = true): Boolean = with(EroStandard) {
    (types.isEmpty() || type in types) &&
        (mark.not() || bookmarks(marks)) &&
        (pages(pages)) &&
        (match(tagExclude).not()) &&
        (user(userExclude).not())
}

// endregion

// region Read Info

internal fun File.readIllustInfo() = PixivJson.decodeFromString(IllustInfo.serializer(), readText())

internal fun File.readUgoiraMetadata() = PixivJson.decodeFromString(UgoiraMetadata.serializer(), readText())

internal fun IllustInfo.write(file: File = illust(pid)) {
    file.parentFile.mkdirs()
    file.writeText(PixivJson.encodeToString(IllustInfo.serializer(), this))
}

internal fun UgoiraMetadata.write(file: File) {
    file.parentFile.mkdirs()
    file.writeText(PixivJson.encodeToString(UgoiraMetadata.serializer(), this))
}

internal fun Collection<IllustInfo>.write() = onEach { it.write() }

private val FlushIllustInfo: suspend PixivAppClient.(Long) -> IllustInfo = { pid ->
    val illust = illustDetail(pid).illust
    if (illust.user.id == 0L) throw RestrictException(illust = illust)
    illust.replicate()
    illust
}

internal suspend fun PixivAppClient.getIllustInfo(
    pid: Long,
    flush: Boolean,
    block: suspend PixivAppClient.(Long) -> IllustInfo = FlushIllustInfo,
): IllustInfo {
    val file = illust(pid)
    return if (!flush && file.exists()) {
        try {
            file.readIllustInfo()
        } catch (e: Throwable) {
            logger.warning({ "文件${file.absolutePath}读取存在问题" }, e)
            throw e
        }
    } else {
        block(pid).apply { write(file = file) }
    }
}

// endregion

// region Download Image

internal suspend fun UserInfo.getProfileImage(): File {
    val image = Url(profileImageUrls.values.lastOrNull() ?: NO_PROFILE_IMAGE)
    return ProfileFolder.resolve(image.filename).apply {
        if (exists().not()) {
            writeBytes(PixivHelperDownloader.download(url = image))
            logger.info { "用户 $image 下载完成" }
        }
    }
}

internal suspend fun IllustInfo.getImages(): List<File> {
    val folder = images(pid).apply { mkdirs() }

    /**
     * 全部Url
     */
    val urls = getOriginImageUrls().filter { "$pid" in it.encodedPath }

    /**
     * 需要下载的 Url
     */
    val downloads = mutableListOf<Url>()

    /**
     * 过滤Url
     */
    val files = urls.map { url ->
        folder.resolve(url.filename).apply {
            if (exists().not()) {
                downloads.add(url)
            }
        }
    }

    if (downloads.isNotEmpty()) {

        fun FileInfo(url: Url, bytes: ByteArray) = FileInfo(
            pid = pid,
            index = with(url.encodedPath) {
                val end = lastIndexOf('.')
                val start = lastIndexOf('p', end) + 1
                substring(start, end)
                    .toIntOrNull() ?: throw NoSuchElementException(url.encodedPath)
            },
            md5 = bytes.toByteString().md5().hex(),
            url = url.toString(),
            size = bytes.size
        )

        val results = mutableListOf<FileInfo>()
        var size = 0L

        downloads.removeIf { url ->
            val file = TempFolder.resolve(url.filename)
            val exists = file.exists()
            if (exists) {
                logger.info { "从[${file}]移动文件" }
                results.add(FileInfo(url = url, bytes = file.readBytes()))
                file.renameTo(folder.resolve(url.filename))
            } else {
                false
            }
        }

        PixivHelperDownloader.downloadImageUrls(urls = downloads) { url, deferred ->
            try {
                val bytes = deferred.await()
                TempFolder.resolve(url.filename).writeBytes(bytes)
                size += bytes.size
                results += FileInfo(url = url, bytes = bytes)
            } catch (e: Throwable) {
                logger.warning({ "[$url]下载失败" }, e)
            }
        }

        if (pid !in ArtWorkInfo) this.replicate()
        results.replicate()

        logger.info {
            "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${bytes(size)}]{${totalBookmarks}}下载完成"
        }

        for (url in downloads) {
            TempFolder.resolve(url.filename).apply {
                if (exists()) renameTo(folder.resolve(url.filename))
            }
        }
    }
    return files
}

internal suspend fun <T> IllustInfo.useImageResources(block: suspend (Int, ExternalResource) -> T): List<T> {
    if (type == WorkContentType.UGOIRA) {
        return listOf(getUgoira().toExternalResource().use { block(0, it) })
    }

    val folder = images(pid).apply { mkdirs() }
    return supervisorScope {
        getOriginImageUrls().mapIndexed { index, url ->
            async {
                val file = folder.resolve(url.filename).apply {
                    if (exists().not()) {
                        val bytes = PixivHelperDownloader.download(url)
                        val info = FileInfo(
                            pid = pid,
                            index = index,
                            md5 = bytes.toByteString().md5().hex(),
                            url = url.toString(),
                            size = bytes.size
                        )
                        info.replicate()
                        folder.resolve(url.filename).writeBytes(bytes)
                    }
                }

                file.toExternalResource().use { resource -> block(index, resource) }
            }
        }
    }.awaitAll()
}

internal suspend fun IllustInfo.getUgoira(flush: Boolean = false): File {
    val json = ugoira(pid)
    val metadata = json.takeIf { it.exists() }?.readUgoiraMetadata()
        ?: PixivAuthClient().ugoiraMetadata(pid).ugoira.also { it.write(json) }
    return try {
        PixivSkikoGifEncoder.build(illust = this, metadata = metadata, flush = flush)
    } catch (cause: Throwable) {
        PixivHelperGifEncoder.build(illust = this, metadata = metadata, flush = flush)
    }
}

internal suspend fun SpotlightArticle.getThumbnailImage(): File {
    val image = Url(thumbnail)
    return ArticleFolder.resolve(image.filename).apply {
        if (exists().not()) {
            writeBytes(PixivHelperDownloader.download(url = image))
            logger.info { "特辑封面 $image 下载完成" }
        }
    }
}

internal suspend fun CreatorDetail.getCoverImage(): File? {
    val image = Url(coverImageUrl ?: return null)
    return ProfileFolder.resolve(image.filename).apply {
        if (exists().not()) {
            writeBytes(PixivHelperDownloader.download(url = image))
            logger.info { "用户 $image 下载完成" }
        }
    }
}

// endregion

// region Tool

/**
 * 二进制模，快速表示
 */
internal val bit: (Int) -> Long = { 1L shl it }

/**
 * 进制表示 bytes 大小
 */
internal val bytes: (Long) -> String = {
    when (it) {
        0L -> "0"
        in bit(0) until bit(10) -> "%dB".format(it)
        in bit(10) until bit(20) -> "%dKB".format(it / bit(10))
        in bit(20) until bit(30) -> "%dMB".format(it / bit(20))
        in bit(20) until bit(30) -> "%dGB".format(it / bit(20))
        else -> throw IllegalStateException("Too Big")
    }
}

/**
 * 备份文件
 */
internal fun backups(): Map<String, File> {
    val map = hashMapOf<String, File>()
    if (PixivHelperPlugin.dataFolder.list().isNullOrEmpty().not()) {
        map["DATA"] = PixivHelperPlugin.dataFolder
    }
    if (PixivHelperPlugin.configFolder.list().isNullOrEmpty().not()) {
        map["CONFIG"] = PixivHelperPlugin.configFolder
    }
    val sqlite: String = sqlite()
    if (sqlite.isNotBlank()) {
        map["DATABASE"] = File(sqlite)
    }

    return map
}

/**
 * pixiv.me 跳转
 */
internal suspend fun PixivHelper.redirect(account: String): Long {
    UserBaseInfo[account]?.let { return@redirect it.uid }
    val url = Url("https://pixiv.me/$account")
    val location = location(url = url)
    return requireNotNull(URL_USER_REGEX.find(location)) { "跳转失败, $url -> $location" }.value.toLong()
}

// endregion