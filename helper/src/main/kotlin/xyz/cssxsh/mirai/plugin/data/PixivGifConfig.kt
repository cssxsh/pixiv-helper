package xyz.cssxsh.mirai.plugin.data

import com.squareup.gifencoder.*
import net.mamoe.mirai.console.data.*

object PixivGifConfig : ReadOnlyPluginConfig("PixivGifConfig") {
    val QUANTIZER_LIST = listOf(
        "com.squareup.gifencoder.UniformQuantizer",
        "com.squareup.gifencoder.MedianCutQuantizer",
        "com.squareup.gifencoder.OctTreeQuantizer",
        "com.squareup.gifencoder.KMeansQuantizer",
        "xyz.cssxsh.pixiv.tool.OpenCVQuantizer"
    )

    val DITHERER_LIST = listOf(
        "com.squareup.gifencoder.FloydSteinbergDitherer",
        "com.squareup.gifencoder.NearestColorDitherer",
        "xyz.cssxsh.pixiv.tool.AtkinsonDitherer",
        "xyz.cssxsh.pixiv.tool.JJNDitherer",
        "xyz.cssxsh.pixiv.tool.SierraLiteDitherer",
        "xyz.cssxsh.pixiv.tool.StuckiDitherer"
    )

    @ValueName("quantizer")
    @ValueDescription("编码器")
    val quantizer by value("com.squareup.gifencoder.OctTreeQuantizer")

    @ValueName("ditherer")
    @ValueDescription("抖动器")
    val ditherer by value("xyz.cssxsh.pixiv.tool.AtkinsonDitherer")

    @ValueName("disposal")
    @ValueDescription("切换方法")
    val disposal by value(DisposalMethod.UNSPECIFIED)

    @ValueName("max_count")
    @ValueDescription("OpenCVQuantizer 最大迭代数")
    val maxCount by value(32)
}