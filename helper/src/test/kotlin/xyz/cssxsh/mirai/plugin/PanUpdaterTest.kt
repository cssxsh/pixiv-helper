package xyz.cssxsh.mirai.plugin

import com.soywiz.klock.measureTime
import com.soywiz.klock.toTimeString
import com.soywiz.klock.wrapped.WDateTime
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import xyz.cssxsh.mirai.plugin.PanUpdater.update
import xyz.cssxsh.mirai.plugin.updater.PanConfig

internal class PanUpdaterTest {

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
            ) { data, (count, size) ->
                println("${WDateTime.now().format("yyyy-MM-dd'T'HH:mm:ss")} ${count}/${size}  $data")
            }.join()
        }.let {
            println(it.toTimeString())
        }
    }
}