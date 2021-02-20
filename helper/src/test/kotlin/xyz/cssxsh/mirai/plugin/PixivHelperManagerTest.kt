package xyz.cssxsh.mirai.plugin

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class PixivHelperManagerTest {

    @Test
    fun mapTest() {
        println("sat")
        val testMap = object : LinkedHashMap<Long, String>(0) {

            override fun put(key: Long, value: String) = super.put(key, value).also {
                println(value)
            }

            override fun remove(key: Long) = super.remove(key).also {
                println(this)
            }
        }
        println(testMap.size)
        testMap.put(1, "sss")

        println(testMap.size)

        testMap.getOrPut(1) { "test" }

        testMap.clear()
    }
}