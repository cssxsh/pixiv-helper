package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class PanUpdaterTest {

    @Test
    fun update(): Unit = runBlocking {
        PanUpdater.update("D:\\Downloads\\app-release.apk", "app-release.apk")
    }
}