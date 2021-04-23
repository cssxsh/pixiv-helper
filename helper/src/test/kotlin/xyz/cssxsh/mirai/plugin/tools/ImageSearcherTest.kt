package xyz.cssxsh.mirai.plugin.tools

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import java.io.File

internal class ImageSearcherTest {

    private val picUrl = "https://gchat.qpic.cn/gchatpic_new/1438159989/589573061-2432001077-105D15A0C8388AA5C121418AAD17B8B5/0?term=2"

    private val picFile = "./test/temp.jpg"

    private val ignore: suspend (Throwable) -> Boolean = { false }

    @Test
    fun getSearchResults(): Unit = runBlocking {
        ImageSearcher.getSearchResults(ignore = ignore, url = picUrl).also {
            assert(it.isEmpty().not()) { "搜索结果为空" }
        }.forEach {
            println(it.toString())
        }
    }

    @Test
    fun postSearchResults(): Unit = runBlocking {
        ImageSearcher.postSearchResults(ignore = ignore, file = File(picFile).readBytes()).also {
            assert(it.isEmpty().not()) { "搜索结果为空" }
        }.forEach {
            println(it)
        }
    }

    private val twimg = "https://pbs.twimg.com/media/EaIpDtCVcAA85Hi?format=jpg&name=orig"

    @Test
    fun getTwitterImage(): Unit = runBlocking {
        ImageSearcher.getTwitterImage(ignore = ignore, url = twimg).also {
            assert(it.isEmpty().not()) { "搜索结果为空" }
        }.forEach {
            println(it)
        }
    }
}