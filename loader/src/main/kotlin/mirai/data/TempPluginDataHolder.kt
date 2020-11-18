package mirai.data

import kotlinx.coroutines.CoroutineName
import net.mamoe.mirai.console.data.AutoSavePluginDataHolder
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.hoursToMillis
import net.mamoe.mirai.utils.minutesToMillis
import kotlin.coroutines.CoroutineContext

@ConsoleExperimentalApi
object TempPluginDataHolder : AutoSavePluginDataHolder {
    @ConsoleExperimentalApi
    override val autoSaveIntervalMillis: LongRange
        get() = 3.minutesToMillis..30.hoursToMillis

    override val coroutineContext: CoroutineContext = CoroutineName("TempPluginDataHolder")

    @ConsoleExperimentalApi
    override val dataHolderName: String
        get() = "TempPluginDataHolder"
}