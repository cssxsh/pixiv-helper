package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.jsoup.*
import org.jsoup.nodes.*
import org.jsoup.select.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.tool.*

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
abstract class HtmlParser(var ignore: Ignore) {

    constructor(name: String) : this(ignore = Ignore(name))

    protected open val client = HttpClient(OkHttp) {
        BrowserUserAgent()
        engine {
            config {
                if (PixivHelperSettings.proxy.isNotBlank()) {
                    proxy(Url(PixivHelperSettings.proxy).toProxy())
                } else {
                    sslSocketFactory(RubySSLSocketFactory, RubyX509TrustManager)
                    hostnameVerifier { _, _ -> true }
                }
            }
        }
    }

    protected fun sni(host: Regex) {
        RubySSLSocketFactory.regexes.add(host)
    }

    protected fun Elements.findAll(regex: Regex) = regex.findAll(html())

    protected suspend fun <R> http(block: suspend (HttpClient) -> R): R = supervisorScope {
        while(isActive) {
            runCatching {
                block(client)
            }.onSuccess {
                return@supervisorScope it
            }.onFailure { throwable ->
                if (ignore(throwable)) {
                    // html(transform = transform, block = block)
                } else {
                    throw throwable
                }
            }
        }
        throw CancellationException()
    }

    suspend fun <T> html(transform: (Document) -> T, block: HttpRequestBuilder.() -> Unit): T = http {
        transform(Jsoup.parse(it.request<String>(block)))
    }
}