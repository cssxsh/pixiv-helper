package xyz.cssxsh.mirai.plugin.command

import io.ktor.client.features.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.*
import okhttp3.internal.http2.StreamResetException
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivSearchData.results
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.pixiv.data.*
import xyz.cssxsh.mirai.plugin.tools.ImageSearcher
import java.io.EOFException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

@Suppress("unused")
object PixivSearchCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "search", "搜索", "搜图",
    description = "PIXIV搜索指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private const val MIN_SIMILARITY = 0.70

    private const val MAX_REPEAT = 10

    private val searchApiIgnore: suspend (Throwable) -> Boolean = { throwable ->
        when (throwable) {
            is SSLException,
            is EOFException,
            is ConnectException,
            is SocketTimeoutException,
            is HttpRequestTimeoutException,
            is StreamResetException,
            is UnknownHostException,
            -> {
                logger.warning { "SEARCH API错误, 已忽略: ${throwable.message}" }
                true
            }
            else -> false
        }
    }

    private suspend fun search(url: String) = (1..MAX_REPEAT).fold(null as List<SearchResult>?) { result, index ->
        result ?: runCatching {
            ImageSearcher.getSearchResults(
                ignore = searchApiIgnore,
                url = url.replace("http://", "https://")
            )
        }.onFailure {
            logger.warning({ "搜索[$url]第${index}次, 失败" }, it)
        }.getOrNull()
    }.orEmpty()

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.search(image: Image) = runCatching {
        results.getOrElse(image.getMd5Hex()) {
            search(image.queryUrl()).run {
                requireNotNull(maxByOrNull { it.similarity }) { "没有搜索结果" }
            }
        }.also { result ->
            if (result.similarity > MIN_SIMILARITY) getHelper().runCatching {
                launch {
                    logger.verbose { "[${image.getMd5Hex()}]相似度大于${MIN_SIMILARITY}开始获取搜索结果${result}" }
                    runCatching {
                        addCacheJob(name = "SEARCH[${image.getMd5Hex()}]", reply = false) {
                            listOf(getIllustInfo(pid = result.pid, flush = true))
                        }
                    }.onSuccess {
                        results[image.getMd5Hex()] = result
                    }.onFailure {
                        logger.warning({ "缓存搜索结果失败" }, it)
                    }
                }
            }
        }
    }.onSuccess { result ->
        quoteReply(buildString {
            appendLine("相似度: ${result.similarity * 100}%")
            appendLine(result.content + "#${result.uid}")
        })
    }.onFailure {
        logger.verbose({ "搜索失败$image" }, it)
        quoteReply("搜索失败， ${it.message}")
    }.isSuccess
}