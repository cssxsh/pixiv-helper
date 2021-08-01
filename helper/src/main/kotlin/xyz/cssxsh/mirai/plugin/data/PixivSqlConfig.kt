package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value
import xyz.cssxsh.mirai.plugin.SqlConfig

object PixivSqlConfig : ReadOnlyPluginConfig("PixivSqlConfig"), SqlConfig {
    @ValueDescription("JDBC url")
    override val url by value("jdbc:sqlite:pixiv.sqlite")

    @ValueDescription("JDBC driver")
    override val driver by value("")

    @ValueDescription("JDBC driver properties")
    override val properties by value(emptyMap<String, String>())
}