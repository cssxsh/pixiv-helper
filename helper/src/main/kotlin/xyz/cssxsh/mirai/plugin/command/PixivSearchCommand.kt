package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.MiraiInternalApi
import net.mamoe.mirai.utils.verbose
import net.mamoe.mirai.utils.warning
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperDownloader.getImages
import xyz.cssxsh.mirai.plugin.data.PixivSearchData.resultMap
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.data.SearchResult
import xyz.cssxsh.mirai.plugin.tools.ImageSearcher

object PixivSearchCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "search", "搜索", "搜图",
    description = "PIXIV搜索指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private const val MIN_SIMILARITY = 0.80

    private const val MAX_REPEAT = 10

    private suspend fun search(url: String, repeat: Int = 0): List<SearchResult> = runCatching {
        ImageSearcher.getSearchResults(
            ignore = apiIgnore,
            picUrl = url.replace("http://", "https://")
        )
    }.onFailure {
        logger.warning({ "搜索[$url]第${repeat}次失败" }, it)
        if (repeat >= MAX_REPEAT) {
            throw IllegalStateException("搜索次数超过${MAX_REPEAT}", it)
        }
    }.getOrElse { search(url, repeat + 1) }


    @MiraiInternalApi
    private fun Image.getMd5Hex(): String = md5.toUByteArray().joinToString("") {
        """%02x""".format(it.toInt())
    }

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(image: Image) = runCatching {
        resultMap.getOrElse(image.getMd5Hex()) {
            search(image.queryUrl()).run {
                requireNotNull(maxByOrNull { it.similarity }) { "没有搜索结果" }
            }.also { result ->
                if (result.similarity > MIN_SIMILARITY) getHelper().runCatching {
                    launch {
                        logger.verbose { "[${image.getMd5Hex()}]相似度大于${MIN_SIMILARITY}开始获取搜索结果${result}" }
                        runCatching {
                            getIllustInfo(pid = result.pid, flush = true).apply {
                                getImages(pid, getOriginUrl())
                                saveToSQLite()
                            }
                        }.onSuccess {
                            resultMap[image.getMd5Hex()] = result
                        }.onFailure {
                            logger.warning({ "缓存搜索结果失败" }, it)
                        }
                    }
                }
            }
        }.let { result ->
            buildString {
                appendLine("相似度: ${result.similarity * 100}%")
                appendLine(result.content + "#${result.uid}")
            }
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        logger.verbose({ "搜索失败$image" }, it)
        quoteReply("搜索失败， ${it.message}")
    }.isSuccess
}