package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import xyz.cssxsh.mirai.plugin.Ignore

abstract class HtmlParser(private val ignore: Ignore) {

    constructor(name: String) : this(ignore = Ignore(name))

    protected open val client = HttpClient(OkHttp)

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