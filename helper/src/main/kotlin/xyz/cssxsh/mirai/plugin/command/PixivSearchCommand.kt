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

    private suspend fun other(image: Image) = ImageSearcher.other(url = image.queryUrl())

    private suspend fun pixiv(image: Image) = ImageSearcher.pixiv(url = image.queryUrl())

    private suspend fun json(image: Image) = ImageSearcher.json(url = image.queryUrl())

    private fun List<PixivSearchResult>.save(image: Image) = filter { it.similarity > MIN_SIMILARITY }.apply {
        (maxByOrNull { it.similarity } ?: return@apply).save(image.md5.toByteString().hex())
    }

    private fun find(hash: String): PixivSearchResult? {
        val cache = PixivSearchResult.find(hash)
        if (cache != null) return cache
        val file = FileInfo.find(hash).firstOrNull()
        if (file != null) return PixivSearchResult(md5 = hash, similarity = 1.0, pid = file.pid)
        return null
    }

    private fun SearchResult.translate(): SearchResult {
        return if (this is TwitterSearchResult && md5.isNotBlank()) {
            (find(md5) ?: this)
        } else {
            this
        }
    }

    @Handler
    suspend fun CommandSenderOnMessage<*>.search(image: Image = fromEvent.message.getQuoteImage()) = withHelper {
        logger.info { "搜索 ${image.queryUrl()}" }
        val hash = image.md5.toByteString().hex()

        val record = find(hash)
        if (record != null) return@withHelper record

        val result = if (ImageSearcher.key.isNotBlank()) {
            json(image).run {
                filterIsInstance<PixivSearchResult>().save(image).ifEmpty { this }
            }
        } else {
            pixiv(image).save(image).ifEmpty { other(image) }
        }

        result.maxByOrNull { it.similarity }?.translate()?.getContent() ?: "没有搜索结果"
    }
}