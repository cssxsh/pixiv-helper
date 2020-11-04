package mirai.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

object AmrFileData : AutoSavePluginData("AmrFile") {
    val files: MutableMap<String, String> by value(mutableMapOf())
}