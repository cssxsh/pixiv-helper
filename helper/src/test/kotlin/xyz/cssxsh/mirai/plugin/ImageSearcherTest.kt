package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import xyz.cssxsh.mirai.plugin.tools.ImageSearcher

internal class ImageSearcherTest {
    private val picUrl = "http://gchat.qpic.cn/gchatpic_new/1438159989/589573061-2432001077-105D15A0C8388AA5C121418AAD17B8B5/0?term=2"

    @Test
    fun getSearchResults(): Unit = runBlocking {
        ImageSearcher.getSearchResults(picUrl = picUrl).also {
            assert(it.isEmpty().not()) { "搜索结果为空" }
        }.forEach {
            println(it.toString())
        }
    }

    @Test
    fun postSearchResults(): Unit = runBlocking {
        ImageSearcher.postSearchResults(picUrl = picUrl).also {
            assert(it.isEmpty().not()) { "搜索结果为空" }
        }.forEach {
            println(it)
        }
    }
}