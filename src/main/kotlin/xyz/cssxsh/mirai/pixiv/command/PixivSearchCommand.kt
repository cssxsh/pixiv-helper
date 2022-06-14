package xyz.cssxsh.mirai.pixiv.command

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.console.util.ContactUtils.render
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.*
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import xyz.cssxsh.mirai.hibernate.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.mirai.pixiv.tools.*
import java.io.*

public object PixivSearchCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "search", "搜索", "搜图",
    description = "PIXIV搜索指令，通过 https://saucenao.com/ https://ascii2d.net/"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    private suspend fun CommandSenderOnMessage<*>.getAvatar(): Image? {
        val at = fromEvent.message.findIsInstance<At>() ?: return null
        val user = when (val contact = subject) {
            is Group -> contact[at.target]
            is Friend -> if (contact.id == at.target) contact else null
            else -> null
        } ?: return null
        val original = "https://q.qlogo.cn/g?b=qq&nk=${user.id}&s=0"
        val largest = "https://q.qlogo.cn/g?b=qq&nk=${user.id}&s=640"

        return HttpClient(OkHttp).use { http ->
            try {
                http.get(original).body<ByteArray>().toExternalResource()
            } catch (_: IOException) {
                http.get(largest).body<ByteArray>().toExternalResource()
            }.use {
                fromEvent.subject.uploadImage(it)
            }
        }
    }

    private fun CommandSenderOnMessage<*>.getQuoteImage(): Image? {
        val quote = fromEvent.message.findIsInstance<QuoteReply>() ?: return null
        return MiraiHibernateRecorder[quote.source]
            .firstNotNullOfOrNull { it.toMessageSource().originalMessage.findIsInstance<Image>() }
    }

    private fun CommandSenderOnMessage<*>.getCurrentImage(): Image? {
        val current = fromEvent.message.findIsInstance<Image>()
        if (current != null) return current
        return MiraiHibernateRecorder
            .get(contact = fromEvent.subject, start = fromEvent.time - ImageSearchConfig.wait, end = fromEvent.time)
            .firstNotNullOfOrNull { it.toMessageSource().originalMessage.findIsInstance<Image>() }
    }

    private suspend fun CommandSenderOnMessage<*>.getNextImage(): Image {
        sendMessage("${ImageSearchConfig.wait}s内，请发送图片")
        val next = fromEvent.nextMessage(ImageSearchConfig.wait * 1000L) {
            Image in it.message || FlashImage in it.message
        }
        return next.findIsInstance<Image>() ?: next.firstIsInstance<FlashImage>().image
    }

    private suspend fun saucenao(url: String): List<SearchResult> {
        return try {
            ImageSearcher.saucenao(url = url)
        } catch (e: Throwable) {
            logger.warning({ "saucenao 搜索 $url 失败" }, e)
            emptyList()
        }
    }

    private suspend fun ascii2d(url: String): List<SearchResult> {
        return try {
            ImageSearcher.ascii2d(url = url, bovw = ImageSearchConfig.bovw)
        } catch (e: Throwable) {
            logger.warning({ "ascii2d 搜索 $url 失败" }, e)
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
            is PixivSearchResult -> result.apply { md5 = hash }
            is TwitterSearchResult -> record(result.md5)?.apply { md5 = hash } ?: result
            is OtherSearchResult -> result
        }
    }

    @Handler
    public suspend fun CommandSenderOnMessage<*>.search(): Unit = withHelper {
        val origin = getQuoteImage() ?: getCurrentImage() ?: getAvatar() ?: getNextImage()
        val url = origin.queryUrl()
        logger.info { "${fromEvent.sender.render()} 搜索 $url" }
        val hash = origin.md5.toUHexString("")

        val record = record(hash)
        if (record != null) return@withHelper record.getContent(contact = fromEvent.subject)

        val saucenao = saucenao(url).similarity(MIN_SIMILARITY).translate(hash)

        val records = if (saucenao.none { it.similarity > MIN_SIMILARITY }) {
            saucenao + ascii2d(url).translate(hash)
        } else {
            saucenao
        }

        records.getContent(sender = fromEvent.sender)
    }
}