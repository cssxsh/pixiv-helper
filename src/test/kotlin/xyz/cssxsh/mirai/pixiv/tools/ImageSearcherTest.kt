package xyz.cssxsh.mirai.pixiv.tools

import io.ktor.client.features.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import java.io.*

internal class ImageSearcherTest {

    private val picUrl = ""

    init {
        ImageSearcher.ignore = {
            println(it)
            it is IOException || it is HttpRequestTimeoutException
        }
    }

    @Test
    fun json(): Unit = runBlocking {
        ImageSearcher.json(url = picUrl).also {
            assert(it.isEmpty().not()) { "搜索结果为空" }
        }.forEach {
            println(it.toString())
        }
    }

    private val twimg = "https://pbs.twimg.com/media/EaIpDtCVcAA85Hi?format=jpg&name=orig"

    @Test
    fun other(): Unit = runBlocking {
        ImageSearcher.html(url = twimg).also {
            assert(it.isEmpty().not()) { "搜索结果为空" }
        }.forEach {
            println(it)
        }
    }
}