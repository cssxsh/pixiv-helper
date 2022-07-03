package xyz.cssxsh.mirai.pixiv.data

import com.squareup.gifencoder.*
import net.mamoe.mirai.console.data.*

public object PixivGifConfig : ReadOnlyPluginConfig("PixivGifConfig") {
    public val QUANTIZER_LIST: List<String> = listOf(
        "com.squareup.gifencoder.UniformQuantizer",
        "com.squareup.gifencoder.MedianCutQuantizer",
        "com.squareup.gifencoder.OctTreeQuantizer",
        "com.squareup.gifencoder.KMeansQuantizer",
        "xyz.cssxsh.pixiv.tool.OpenCVQuantizer"
    )

    public val DITHERER_LIST: List<String> = listOf(
        "com.squareup.gifencoder.FloydSteinbergDitherer",
        "com.squareup.gifencoder.NearestColorDitherer",
        "xyz.cssxsh.pixiv.tool.AtkinsonDitherer",
        "xyz.cssxsh.pixiv.tool.JJNDitherer",
        "xyz.cssxsh.pixiv.tool.SierraLiteDitherer",
        "xyz.cssxsh.pixiv.tool.StuckiDitherer"
    )

    @ValueName("quantizer")
    @ValueDescription("编码器")
    public val quantizer: String by value("com.squareup.gifencoder.OctTreeQuantizer")

    @ValueName("ditherer")
    @ValueDescription("抖动器")
    public val ditherer: String by value("xyz.cssxsh.pixiv.tool.AtkinsonDitherer")

    @ValueName("disposal")
    @ValueDescription("切换方法")
    public val disposal: DisposalMethod by value(DisposalMethod.UNSPECIFIED)

    @ValueName("max_count")
    @ValueDescription("OpenCVQuantizer 最大迭代数")
    public val maxCount: Int by value(32)
}