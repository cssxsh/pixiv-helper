package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import xyz.cssxsh.mirai.plugin.tools.PanUpdater.update
import xyz.cssxsh.mirai.plugin.tools.PanConfig
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

internal class PanUpdaterTest {

    @ExperimentalTime
    @Test
    fun update(): Unit = runBlocking {
        val filepath = "D:\\Downloads\\Linux\\PixivCache.json"
        measureTime {
            update(
                sourcePath = filepath,
                updatePath = filepath.getFilename(),
                config = PanConfig(
                    bdsToken = "a25846e85d9ae2b6356b1c78d31c9ef3",
                    logId = "MjA3ODZFQzFBMUNFODVDRjdFRkVBMUZGMkZBOTdBM0Y6Rkc9MQ==",
                    targetPath = "/Pixiv",
                    cookies = "BDUSS=JnNUVzZTBIRjBxSm10dTVtQ01Mb01nNDNhYkxzck5hTVZsRH5GRGJTdzhyNjFmSUFBQUFBJCQAAAAAAAAAAAEAAADUV~MytLTKwMnx0KHJ-ruvAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADwihl88IoZfTz; BDUSS_BFESS=JnNUVzZTBIRjBxSm10dTVtQ01Mb01nNDNhYkxzck5hTVZsRH5GRGJTdzhyNjFmSUFBQUFBJCQAAAAAAAAAAAEAAADUV~MytLTKwMnx0KHJ-ruvAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADwihl88IoZfTz; STOKEN=0119324ef6417dfc810b4da1c76267b37d3e50d3d6a72f345ff751a453f2a93f"
                )
            ) { data, count, size ->
                println("${OffsetDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)} ${count}/${size} $data")
            }.join()
        }.let {
            println("${it.inMilliseconds} ms")
        }
    }
}