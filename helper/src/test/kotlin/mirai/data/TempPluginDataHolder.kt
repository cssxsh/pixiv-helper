package mirai.data

import net.mamoe.mirai.console.data.PluginDataHolder
import net.mamoe.mirai.console.util.ConsoleExperimentalApi

@ConsoleExperimentalApi
object TempPluginDataHolder : PluginDataHolder {
    @ConsoleExperimentalApi
    override val dataHolderName: String
        get() = "TempPluginDataHolder"
}