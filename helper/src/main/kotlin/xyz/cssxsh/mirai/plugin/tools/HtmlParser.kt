package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import xyz.cssxsh.mirai.plugin.Ignore

abstract class HtmlParser(private val ignore: Ignore) {

    constructor(name: String): this(ignore = Ignore(name))

    protected open fun client() = HttpClient(OkHttp)

    protected suspend fun html(block: HttpRequestBuilder.() -> Unit): Document {
        return runCatching {
            client().use {
                it.request<String>(block = block)
            }.let {
                Jsoup.parse(it)
            }
        }.getOrElse { throwable ->
            if (ignore(throwable)) {
                html(block = block)
            } else {
                throw throwable
            }
        }
    }

    protected fun Elements.findAll(regex: Regex) = regex.findAll(html())

    protected suspend fun <T> html(transform: (Document) -> T, block: HttpRequestBuilder.() -> Unit): T {
        return runCatching {
            client().use {
                it.request<String>(block = block)
            }.let {
                transform(Jsoup.parse(it))
            }
        }.getOrElse { throwable ->
            if (ignore(throwable)) {
                html(transform = transform, block = block)
            } else {
                throw throwable
            }
        }
    }
}