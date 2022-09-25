package xyz.cssxsh.mirai.pixiv.tools

import kotlinx.coroutines.*
import org.junit.jupiter.api.*

internal class ImageSearcherTest {

    private val picUrl = ""

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