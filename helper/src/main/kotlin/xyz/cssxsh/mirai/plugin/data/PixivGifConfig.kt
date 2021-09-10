package xyz.cssxsh.mirai.plugin.data

import com.squareup.gifencoder.DisposalMethod
import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object PixivGifConfig : ReadOnlyPluginConfig("PixivGifConfig") {
    @ValueDescription("编码器")
    val quantizer by value("com.squareup.gifencoder.MedianCutQuantizer")

    @ValueDescription("抖动器")
    val ditherer by value("com.squareup.gifencoder.FloydSteinbergDitherer")

    @ValueDescription("切换方法")
    val disposal by value(DisposalMethod.UNSPECIFIED)
}