package xyz.cssxsh.mirai.plugin.data

import com.soywiz.klock.wrapped.WDateTimeTz
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.cssxsh.pixiv.data.app.IllustInfo

@Serializable
data class TagData(
    @SerialName("tags")
    val tags: Map<String, Int>,
    @Serializable(with = IllustInfo.Companion.CreateDateSerializer::class)
    val lastTime: WDateTimeTz = WDateTimeTz.nowLocal()
)