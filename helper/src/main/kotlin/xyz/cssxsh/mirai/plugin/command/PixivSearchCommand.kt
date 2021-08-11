package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.*
import okio.ByteString.Companion.toByteString
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.mirai.plugin.tools.*

object PixivSearchCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "search", "搜索", "搜图",
    description = "PIXIV搜索指令，通过https://saucenao.com/"
) {

    override val prefixOptional: Boolean = true

    private val current by PixivHelperListener::current

    private val images by PixivHelperListener::images

    private fun MessageChain.getQuoteImage(): Image {
        val source = requireNotNull(findIsInstance<QuoteReply>()?.source ?: current[source.targetId]) { "没有回复消息" }
        return requireNotNull(images[source.metadata()]) { "图片历史未找到" }
    }

    private suspend fun html(image: Image) = ImageSearcher.html(url = image.queryUrl())

    private suspend fun json(image: Image) = ImageSearcher.json(url = image.queryUrl())

    private fun List<SearchResult>.similarity(min: Double = MIN_SIMILARITY): SearchResult? {
        return filterIsInstance<PixivSearchResult>()
            .filter { it.similarity > min }
            .ifEmpty { this }
            .maxByOrNull { it.similarity }
    }


    private fun record(hash: String): PixivSearchResult? {
        if (hash.isNotBlank()) return null
        val cache = PixivSearchResult.find(hash)
        if (cache != null) return cache
        val file = FileInfo.find(hash).firstOrNull()
        if (file != null) return PixivSearchResult(md5 = hash, similarity = 1.0, pid = file.pid)
        return null
    }

    private fun SearchResult.translate(hash: String): SearchResult {
        return when (this) {
            is PixivSearchResult -> apply { md5 = hash }
            is TwitterSearchResult -> record(md5)?.apply { md5 = hash } ?: this
            is OtherSearchResult -> this
        }
    }

    @Handler
    suspend fun CommandSenderOnMessage<*>.search(image: Image = fromEvent.message.getQuoteImage()) = withHelper {
        logger.info { "搜索 ${image.queryUrl()}" }
        val hash = image.md5.toByteString().hex()

        val record = record(hash)
        if (record != null) return@withHelper record.getContent()

        val result = if (ImageSearcher.key.isNotBlank()) json(image) else html(image)

        result.similarity()?.translate(hash)?.getContent() ?: "没有搜索结果"
    }
}