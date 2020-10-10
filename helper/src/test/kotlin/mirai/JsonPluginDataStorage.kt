package mirai

import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.data.MultiFilePluginDataStorage
import net.mamoe.mirai.console.data.PluginData
import net.mamoe.mirai.console.data.PluginDataHolder
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.*
import java.io.File
import java.nio.file.Path

@ConsoleExperimentalApi
class JsonPluginDataStorage(
    override val directoryPath: Path,
    private val logger: MiraiLogger = SilentLogger
) : MultiFilePluginDataStorage {
    init {
        directoryPath.toFile().mkdir()
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        allowStructuredMapKeys = true
    }

    override fun load(holder: PluginDataHolder, instance: PluginData) {
        instance.onInit(holder, this)

        val text = getPluginDataFile(holder, instance).readText()
        if (text.isNotBlank()) {
            logger.warning { "Deserializing $text" }
            json.decodeFromString(instance.updaterSerializer, text)
        } else {
            this.store(holder, instance) // save an initial copy
        }
        logger.verbose { "Successfully loaded PluginData: ${instance.saveName}" }
    }

    private fun getPluginDataFile(holder: PluginDataHolder, instance: PluginData): File = directoryPath.run {
        resolve(holder.dataHolderName).toFile()
    }.also { path ->
        require(path.isFile.not()) {
            "Target directory $path for holder $holder is occupied by a file therefore data ${instance::class.qualifiedName} can't be saved."
        }
        path.mkdir()
    }.resolve("${instance.saveName}.json").also { file ->
        require(file.isDirectory.not()) {
            "Target File $file is occupied by a directory therefore data ${instance::class.qualifiedName} can't be saved."
        }
        logger.verbose { "File allocated for ${instance.saveName}: ${file.toURI()}" }
        file.createNewFile()
    }

    override fun store(holder: PluginDataHolder, instance: PluginData) {
        getPluginDataFile(holder, instance).writeText(
            kotlin.runCatching {
                json.encodeToString(instance.updaterSerializer, Unit.INSTANCE)
            }.getOrElse {
                throw IllegalStateException("Exception while saving $instance, saveName=${instance.saveName}", it)
            }
        )
        logger.verbose { "Successfully saved PluginData: ${instance.saveName}" }
    }
}