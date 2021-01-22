package mirai.data

import kotlinx.coroutines.CoroutineName
import net.mamoe.mirai.console.data.AutoSavePluginDataHolder
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.hours
import kotlin.time.minutes

@ConsoleExperimentalApi
object TempPluginDataHolder : AutoSavePluginDataHolder {
    @ExperimentalTime
    @ConsoleExperimentalApi
    override val autoSaveIntervalMillis: LongRange
        get() = (3).minutes.toLongMilliseconds()..(30).hours.toLongMilliseconds()

    override val coroutineContext: CoroutineContext = CoroutineName("TempPluginDataHolder")

    @ConsoleExperimentalApi
    override val dataHolderName: String
        get() = "TempPluginDataHolder"
}