package xyz.cssxsh.mirai.plugin.tools

import io.ktor.client.features.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import java.io.*

internal class ImageSearcherTest {

    private val picUrl = "https://gchat.qpic.cn/gchatpic_new/1438159989/589573061-2432001077-105D15A0C8388AA5C121418AAD17B8B5/0?term=2"

    init {
        ImageSearcher.ignore = {
            println(it)
            it is IOException || it is HttpRequestTimeoutException
        }
    }

    @Test
    fun pixiv(): Unit = runBlocking {
        ImageSearcher.pixiv(url = picUrl).also {
            assert(it.isEmpty().not()) { "搜索结果为空" }
        }.forEach {
            println(it.toString())
        }
    }

    private val twimg = "https://pbs.twimg.com/media/EaIpDtCVcAA85Hi?format=jpg&name=orig"

    @Test
    fun other(): Unit = runBlocking {
        ImageSearcher.other(url = twimg).also {
            assert(it.isEmpty().not()) { "搜索结果为空" }
        }.forEach {
            println(it)
        }
    }
}