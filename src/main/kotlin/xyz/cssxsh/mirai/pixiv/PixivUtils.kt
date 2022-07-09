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
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.exception.*
import xyz.cssxsh.pixiv.fanbox.*
import java.io.*

// region Send Message

internal val SendLimit = """本群每分钟只能发\d+条消息""".toRegex()

internal suspend fun UserCommandSender.quoteReply(message: Message): MessageReceipt<Contact>? {
    return if (this is CommandSenderOnMessage<*>) {
        sendMessage(message = fromEvent.message.quote() + message)
    } else {
        sendMessage(message = At(user) + message)
    }
}

internal suspend fun UserCommandSender.quoteReply(message: String): MessageReceipt<Contact>? {
    return quoteReply(message = message.toPlainText())
}

internal suspend fun UserCommandSender.withHelper(block: suspend PixivHelper.() -> Any?) {
    try {
        when (val message = subject.helper.block()) {
            null, Unit -> Unit
            is ForwardMessage -> {
                check(message.nodeList.size <= 200) {
                    throw MessageTooLargeException(
                        subject, message, message,
                        "ForwardMessage allows up to 200 nodes, but found ${message.nodeList.size}"
                    )
                }
                try {
                    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
                    sendMessage(message + net.mamoe.mirai.internal.message.flags.IgnoreLengthCheck)
                } catch (_: Throwable) {
                    sendMessage(message)
                }
            }
            is Message -> quoteReply(message)
            is String -> quoteReply(message)
            is IllustInfo -> sendIllust(message)
            is ArtWorkInfo -> sendArtwork(message)
            else -> quoteReply(message.toString())
        }
    } catch (cause: Throwable) {
        logger.warning({ "消息回复失败" }, cause)
        when {
            cause is AppApiException -> {
                quoteReply("ApiException, ${cause.json}")
            }
            cause is RestrictException -> {
                launch(SupervisorJob()) {
                    if (cause.illust.pid in ArtWorkInfo) {
                        ArtWorkInfo.delete(pid = cause.illust.pid, comment = cause.message)
                    } else {
                        cause.illust.toArtWorkInfo().copy(caption = cause.message).merge()
                    }
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
    }
}

internal suspend fun CommandSenderOnMessage<*>.withHelper(block: suspend PixivHelper.() -> Any?) {
    (this as UserCommandSender).withHelper(block)
}

internal suspend fun UserCommandSender.sendIllust(illust: IllustInfo) {
    val message: MessageChain = buildIllustMessage(illust = illust, contact = subject)
    withTimeout(3 * 60 * 1000L) {
        when (val model = subject.helper.model) {
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
}

internal suspend fun UserCommandSender.sendArtwork(info: ArtWorkInfo) {
    sendIllust(illust = loadIllustInfo(pid = info.pid, client = subject.helper.client))
}

/**
 * 查找Contact
 */
internal fun findContact(delegate: Long): Contact? {
    for (bot in Bot.instances.shuffled()) {
        for (friend in bot.friends) {
            if (friend.id == delegate) return friend
        }
        for (group in bot.groups) {
            if (group.id == delegate) return group
            for (member in group.members) {
                if (member.id == delegate) return member
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

public suspend fun SearchResult.getContent(contact: Contact): MessageChain = buildMessageChain {
    appendLine("相似度: ${similarity * 100}%")
    when (this@getContent) {
        is PixivSearchResult -> {
            if (similarity > MIN_SIMILARITY) associate()
            appendLine("作者: $name ")
            appendLine("UID: $uid ")
            appendLine("标题: $title ")
            appendLine("PID: $pid ")

            contact.launch {
                try {
                    val illust = loadIllustInfo(pid = pid, flush = true, client = contact.helper.client)
                    if (illust.age > AgeLimit.ALL || illust.pageCount > 3 || illust.type == WorkContentType.MANGA) return@launch
                    val message = buildIllustMessage(illust = illust, contact = contact)
                    contact.sendMessage(message)
                } catch (cause: Throwable) {
                    logger.warning({ "搜索结果自动发送失败" }, cause)
                }
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

public suspend fun List<SearchResult>.getContent(sender: User): Message {
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

//internal suspend fun SpotlightArticle.getContent(contact: Contact) = buildMessageChain {
//    appendLine("AID: $aid")
//    appendLine("标题: $title")
//    appendLine("发布: $publish")
//    appendLine("类别: $category")
//    appendLine("类型: $type")
//    appendLine("链接: $url")
//    append(getThumbnailImage().uploadAsImage(contact))
//}
//
//internal suspend fun PixivHelper.buildMessageByArticle(articles: List<SpotlightArticle>) = buildMessageChain {
//    appendLine("共 ${articles.size} 个特辑")
//    for (article in articles) {
//        appendLine("================")
//        append(article.getContent(contact))
//    }
//}


public suspend fun buildIllustMessage(illust: IllustInfo, contact: Contact): MessageChain {
    val helper = contact.helper
    return buildMessageChain {
        add(illust.getContent(helper.link, helper.tag, helper.attr))
        if (illust.age != AgeLimit.ALL) {
            add("R18禁止！".toPlainText())
            return@buildMessageChain
        }
        illust.useImageResources { index, resource ->
            when {
                index == helper.max -> {
                    logger.warning { "[${illust.pid}](${illust.pageCount})图片过多" }
                    add("部分图片省略\n".toPlainText())
                }
                index < helper.max -> {
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
    }
}

//
//internal suspend fun PixivHelper.buildMessageByUser(preview: UserPreview) = buildMessageChain {
//    appendLine("NAME: ${preview.user.name}")
//    appendLine("UID: ${preview.user.id}")
//    appendLine("ACCOUNT: ${preview.user.account}")
//    appendLine("FOLLOWED: ${preview.user.isFollowed}")
//    appendLine("TWITTER: ${Twitter[preview.user.id].joinToString { it.screen }}")
//    try {
//        append(preview.user.getProfileImage().uploadAsImage(contact))
//    } catch (e: Throwable) {
//        logger.warning({ "User(${preview.user.id}) ProfileImage 下载失败" }, e)
//    }
//    for (illust in preview.illusts.apply { replicate() }.write()) {
//        if (illust.isEro().not()) continue
//        try {
//            if (illust.age == AgeLimit.ALL) {
//                illust.useImageResources { index, resource ->
//                    if (index < 1) add(resource.uploadAsImage(contact))
//                }
//            }
//        } catch (e: Throwable) {
//            logger.warning({ "User(${preview.user.id}) PreviewImage 下载失败" }, e)
//        }
//    }
//}
//
//internal suspend fun PixivHelper.buildMessageByUser(detail: UserDetail) = buildMessageChain {
//    appendLine("NAME: ${detail.user.name}")
//    appendLine("UID: ${detail.user.id}")
//    appendLine("ACCOUNT: ${detail.user.account}")
//    appendLine("FOLLOWED: ${detail.user.isFollowed}")
//    appendLine("TOTAL: ${detail.profile.totalArtwork}")
//    appendLine("TWITTER: ${detail.twitter()}")
//    try {
//        append(detail.user.getProfileImage().uploadAsImage(contact))
//    } catch (e: Throwable) {
//        logger.warning({ "User(${detail.user.id}) ProfileImage 下载失败" }, e)
//    }
//}
//
//internal suspend fun PixivHelper.buildMessageByUser(uid: Long) = buildMessageByUser(detail = userDetail(uid))
//
//internal suspend fun PixivHelper.buildMessageByCreator(creator: CreatorDetail) = buildMessageChain {
//    appendLine("NAME: ${creator.user.name}")
//    appendLine("UID: ${creator.user.userId}")
//    appendLine("CREATOR_ID: ${creator.creatorId}")
//    appendLine("HAS_ADULT_CONTENT: ${creator.hasAdultContent}")
//    appendLine("TWITTER: ${creator.twitter()}")
//    appendLine(creator.description)
//    try {
//        append(creator.getCoverImage()?.uploadAsImage(contact) ?: "".toPlainText())
//    } catch (e: Throwable) {
//        logger.warning({ "Creator(${creator.creatorId}) CoverImage 下载失败" }, e)
//    }
//}

internal fun IllustInfo.getMirrorUrls(): List<Url> {
    return getOriginImageUrls().map { url ->
        URLBuilder(url).apply {
            host = ProxyMirror.ifBlank { PixivMirrorHost }
        }.build()
    }
}

internal fun IllustInfo.isEro(mark: Boolean = true): Boolean = with(EroStandard) {
    (types.isEmpty() || type in types) &&
        (mark.not() || (totalBookmarks ?: 0) > marks) &&
        (pageCount < pages) &&
        (tags.none { tagExclude in it.name || tagExclude in it.translatedName.orEmpty() }) &&
        (user.id !in userExclude)
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

private val FlushIllustInfo: suspend PixivAppClient.(Long, File) -> IllustInfo = { pid, file ->
    val illust = illustDetail(pid = pid).illust
    if (illust.user.id == 0L) throw RestrictException(illust = illust)
    illust.merge()
    illust.write(file = file)
    illust
}

public suspend fun loadIllustInfo(
    pid: Long,
    flush: Boolean = false,
    client: PixivAppClient = PixivClientPool.free(),
    load: suspend PixivAppClient.(Long, File) -> IllustInfo = FlushIllustInfo,
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
        client.load(pid, file)
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
                            md5 = bytes.md5().toUHexString(""),
                            url = url.toString(),
                            size = bytes.size
                        )
                        try {
                            info.merge()
                        } catch (cause: RuntimeException) {
                            useSession { session ->
                                session.transaction.begin()
                                session.remove(info)
                                session.transaction.commit()
                                session.transaction.begin()
                                session.persist(info)
                                session.transaction.commit()
                            }
                        }
                        folder.resolve(url.filename).writeBytes(bytes)
                    }
                }

                file.toExternalResource().use { resource -> block(index, resource) }
            }
        }.awaitAll()
    }
}

internal suspend fun IllustInfo.getUgoira(flush: Boolean = false): File {
    val json = ugoira(pid)
    val metadata = json.takeIf { it.exists() }?.readUgoiraMetadata()
        ?: PixivClientPool.free().ugoiraMetadata(pid).ugoira.also { it.write(json) }
    return try {
        PixivSkikoGifEncoder.build(illust = this, metadata = metadata, flush = flush)
    } catch (_: NoClassDefFoundError) {
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
//internal suspend fun PixivHelper.redirect(account: String): Long {
//    UserBaseInfo[account]?.let { return@redirect it.uid }
//    val url = Url("https://pixiv.me/$account")
//    val location = location(url = url)
//    return requireNotNull(URL_USER_REGEX.find(location)) { "跳转失败, $url -> $location" }.value.toLong()
//}

// endregion