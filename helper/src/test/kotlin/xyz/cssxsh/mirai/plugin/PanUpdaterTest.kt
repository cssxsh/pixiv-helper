package xyz.cssxsh.mirai.plugin

import com.soywiz.klock.measureTime
import com.soywiz.klock.toTimeString
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import xyz.cssxsh.mirai.plugin.PanUpdater.update

internal class PanUpdaterTest {

    @Test
    fun update(): Unit = runBlocking {
        measureTime {
            update("D:\\Downloads\\Linux\\2053497.zip", "2053497.zip") {
                println(it)
            }.join()
        }.let {
            println(it.toTimeString())
        }
    }
}