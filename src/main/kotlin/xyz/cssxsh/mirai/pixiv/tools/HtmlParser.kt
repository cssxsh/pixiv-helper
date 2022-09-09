package xyz.cssxsh.mirai.pixiv.tools

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import net.mamoe.mirai.utils.*
import org.jsoup.*
import org.jsoup.nodes.*
import org.jsoup.select.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.tool.*
import java.io.IOException

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
public abstract class HtmlParser(public val name: String) {
    protected val logger: MiraiLogger by lazy { MiraiLogger.Factory.create(this::class, identity = name) }

    protected fun ignore(throwable: Throwable): Boolean {
        return when (throwable) {
            is IOException -> {
                logger.warning { "$name Api 错误, 已忽略: ${throwable.message}" }
                true
            }
            else -> false
        }
    }

    protected open val client: HttpClient = HttpClient(OkHttp) {
        BrowserUserAgent()
        engine {
            config {
                if (ProxyApi.isNotBlank()) {
                    proxy(Url(ProxyApi).toProxy())
                } else {
                    sslSocketFactory(RubySSLSocketFactory, RubyX509TrustManager)
                    hostnameVerifier { _, _ -> true }
                    dns(RubyDns(JAPAN_DNS, PIXIV_HOST))
                }
            }
        }
    }

    protected fun sni(host: Regex): Boolean = RubySSLSocketFactory.regexes.add(host)

    protected fun Elements.findAll(regex: Regex): Sequence<MatchResult> = regex.findAll(html())

    protected fun Element.href(): String = attr("href")

    protected suspend fun <R> http(block: suspend (HttpClient) -> R): R = supervisorScope {
        var cause: Throwable? = null
        while (isActive) {
            try {
                return@supervisorScope block(client)
            } catch (throwable: Throwable) {
                if (ignore(throwable)) {
                    cause = throwable
                } else {
                    throw throwable
                }
            }
        }
        throw CancellationException(null, cause)
    }

    public suspend fun <T> html(transform: (Document) -> T, block: HttpRequestBuilder.() -> Unit): T = http {
        transform(Jsoup.parse(it.request(block).bodyAsText()))
    }
}