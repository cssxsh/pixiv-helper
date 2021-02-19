package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import xyz.cssxsh.mirai.plugin.tools.BaiduPanUpdater
import java.io.File
import kotlin.time.measureTime

internal class BaiduPanUpdaterTest {

    private fun readConfig() =
        Json.decodeFromString(BaiduPanUpdater.PanConfig.serializer(), File("./test/PanConfig.json").readText())

    @Test
    fun updateTest(): Unit = runBlocking {
        val file = File("./test/DATA.zip")
        BaiduPanUpdater.loadPanConfig(readConfig())
        measureTime {
            BaiduPanUpdater.update(
                file = file,
                updatePath = file.name
            )
        }.let {
            println(it)
        }
    }
}