package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.console.util.ContactUtils.render
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
    "search", "搜索", "搜图",
    description = "PIXIV搜索指令，通过 https://saucenao.com/ https://ascii2d.net/"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    private fun CommandSenderOnMessage<*>.getQuoteImage(): Image? {
        val quote = fromEvent.message.findIsInstance<QuoteReply>() ?: return null
        return MiraiHibernateRecorder[quote.source]
            .firstNotNullOfOrNull { it.toMessageSource().originalMessage.findIsInstance<Image>() }
    }

    private fun CommandSenderOnMessage<*>.getCurrentImage(): Image? {
        return MiraiHibernateRecorder.get(contact = fromEvent.subject, start = 600, end = fromEvent.time)
            .reversed()
            .firstNotNullOfOrNull { it.toMessageSource().originalMessage.findIsInstance<Image>() }
    }

    private fun CommandSenderOnMessage<*>.getNextImage(): Image? = runBlocking(coroutineContext) {
        sendMessage("${ImageSearchConfig.wait}s内，请发送图片")
        fromEvent.nextMessageOrNull(ImageSearchConfig.wait * 1000L) { Image in it.message }?.firstIsInstance()
    }

    private suspend fun saucenao(image: Image) = with(ImageSearcher) {
        try {
            if (key.isBlank()) html(url = image.queryUrl()) else json(url = image.queryUrl())
        } catch (e: Throwable) {
            logger.warning({ "saucenao 搜索 $image 失败" }, e)
            emptyList()
        }
    }

    private suspend fun ascii2d(image: Image) = with(ImageSearcher) {
        try {
            ascii2d(url = image.queryUrl(), bovw = ImageSearchConfig.bovw)
        } catch (e: Throwable) {
            logger.warning({ "ascii2d 搜索 $image 失败" }, e)
            emptyList()
        }
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
        val cache = PixivSearchResult[hash]
        if (cache != null) return cache
        val file = FileInfo[hash].firstOrNull()
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
    @OptIn(ConsoleExperimentalApi::class)
    suspend fun CommandSenderOnMessage<*>.search(image: Image? = null) = withHelper {
        val origin = image ?: getQuoteImage() ?: getCurrentImage() ?: getNextImage() ?: return@withHelper "等待超时"
        logger.info { "${fromEvent.sender.render()} 搜索 ${origin.queryUrl()}" }
        val hash = origin.md5.toByteString().hex()

        val record = record(hash)
        if (record != null) return@withHelper record.getContent(fromEvent.subject)

        val saucenao = saucenao(origin).similarity(MIN_SIMILARITY).translate(hash)

        val result = if (saucenao.none { it.similarity > MIN_SIMILARITY }) {
            saucenao + ascii2d(origin).translate(hash)
        } else {
            saucenao
        }

        result.getContent(fromEvent.sender)
    }
}