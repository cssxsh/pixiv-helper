package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.*
import net.mamoe.mirai.utils.*
import okio.ByteString.Companion.toByteString
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*

object PixivSearchCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "search", "搜索", "搜图", "ascii2d",
    description = "PIXIV搜索指令，通过https://saucenao.com/"
) {

    override val prefixOptional: Boolean = true

    private val current by PixivHelperListener::current

    private val images by PixivHelperListener::images

    private fun CommandSenderOnMessage<*>.getQuoteImage(): Image? {
        val quote = fromEvent.message.findIsInstance<QuoteReply>() ?: return null
        return requireNotNull(images[quote.source.key()]) { "$subject 图片历史未找到" }
    }

    private fun CommandSenderOnMessage<*>.getCurrentImage(): Image? {
        val metadata = current.remove(fromEvent.subject.id) ?: return null
        return requireNotNull(images[metadata]) { "$subject 图片历史未找到" }
    }

    private suspend fun CommandSenderOnMessage<*>.getNextImage(): Image? {
        sendMessage("${ImageSearchConfig.wait}s内，请发送图片")
        return fromEvent.nextMessageOrNull(ImageSearchConfig.wait * 1000L) { Image in it.message }?.firstIsInstance()
    }

    private val CommandSenderOnMessage<*>.isAscii2d get() = "ascii2d" in fromEvent.message.content

    private suspend fun saucenao(image: Image) = with(ImageSearcher) {
        if (key.isBlank()) html(url = image.queryUrl()) else json(url = image.queryUrl())
    }

    private suspend fun ascii2d(image: Image) = with(ImageSearcher) {
        ascii2d(url = image.queryUrl(), bovw = ImageSearchConfig.bovw)
    }

    private fun List<SearchResult>.similarity(min: Double): List<SearchResult> {
        return filterIsInstance<PixivSearchResult>()
            .filter { it.similarity > min }
            .distinctBy { it.pid }
            .ifEmpty { filter { it.similarity > min } }
            .ifEmpty { this }
            .sortedByDescending { it.similarity }
    }

    private fun record(hash: String): PixivSearchResult? {
        if (hash.isNotBlank()) return null
        val cache = PixivSearchResult.find(hash)
        if (cache != null) return cache
        val file = FileInfo.find(hash).firstOrNull()
        if (file != null) return PixivSearchResult(md5 = hash, similarity = 1.0, pid = file.pid)
        return null
    }

    private fun List<SearchResult>.translate(hash: String) = mapIndexedNotNull { index, result ->
        if (index >= ImageSearchConfig.limit) return@mapIndexedNotNull null
        when (result) {
            is PixivSearchResult -> result.apply { result.md5 = hash }
            is TwitterSearchResult -> record(result.md5)?.apply { md5 = hash } ?: result
            is OtherSearchResult -> result
        }
    }

    @Handler
    suspend fun CommandSenderOnMessage<*>.search(image: Image? = null) = withHelper {
        val origin = image ?: getQuoteImage() ?: getCurrentImage() ?: getNextImage() ?: return@withHelper "等待超时"
        logger.info { "搜索 ${origin.queryUrl()}" }
        @OptIn(MiraiInternalApi::class)
        val hash = origin.md5.toByteString().hex()

        val record = record(hash)
        if (record != null) return@withHelper record.getContent()

        val result = if (isAscii2d) ascii2d(origin) else saucenao(origin)

        result.similarity(MIN_SIMILARITY).translate(hash).getContent(user!!)
    }
}