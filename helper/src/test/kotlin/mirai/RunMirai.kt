package mirai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.data.MultiFilePluginDataStorage
import net.mamoe.mirai.console.data.PluginData
import net.mamoe.mirai.console.data.PluginDataHolder
import net.mamoe.mirai.console.data.PluginDataStorage
import net.mamoe.mirai.console.pure.ConsolePureExperimentalApi
import net.mamoe.mirai.console.pure.MiraiConsoleImplementationPure
import net.mamoe.mirai.console.pure.MiraiConsolePureLoader
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.SilentLogger
import net.mamoe.mirai.utils.debug
import net.mamoe.mirai.utils.warning
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths


@ConsoleExperimentalApi
@ConsolePureExperimentalApi
object RunMirai {

    private fun miraiConsoleImpl(rootPath: Path) = MiraiConsoleImplementationPure(
        rootPath = rootPath,
        dataStorageForJvmPluginLoader = JsonPluginDataStorage(rootPath.resolve("data")),
        dataStorageForBuiltIns = JsonPluginDataStorage(rootPath.resolve("data")),
        configStorageForJvmPluginLoader = JsonPluginDataStorage(rootPath.resolve("config")),
        configStorageForBuiltIns = JsonPluginDataStorage(rootPath.resolve("config")),
    )

    @JvmStatic
    fun main(args: Array<String>) {
        // 默认在 /test 目录下运行
        MiraiConsolePureLoader.parse(args, exitProcess = true)
        MiraiConsolePureLoader.startAsDaemon(miraiConsoleImpl(Paths.get(".").toAbsolutePath()))
        try {
            runBlocking {
                MiraiConsole.job.join()
            }
        } catch (e: CancellationException) {
            // ignored
        }
    }

    private class JsonPluginDataStorage(
        override val directoryPath: Path,
        private val logger: MiraiLogger = SilentLogger
    ) : MultiFilePluginDataStorage {
        init {
            directoryPath.toFile().mkdir()
        }

        override fun load(holder: PluginDataHolder, instance: PluginData) {
            instance.onInit(holder, this)

            val text = getPluginDataFile(holder, instance).readText()
            if (text.isNotBlank()) {
                logger.warning { "Deserializing $text" }
                Json.decodeFromString(instance.updaterSerializer, text)
            } else {
                this.store(holder, instance) // save an initial copy
            }
            logger.debug { "Successfully loaded PluginData: ${instance.saveName} (containing ${instance.valueNodes.size} properties)" }
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
            logger.debug { "File allocated for ${instance.saveName}: ${file.toURI()}" }
            file.createNewFile()
        }

        override fun store(holder: PluginDataHolder, instance: PluginData) {
            getPluginDataFile(holder, instance).writeText(
                kotlin.runCatching {
                    Json.encodeToString(instance.updaterSerializer, {}())
                }.getOrElse {
                    throw IllegalStateException("Exception while saving $instance, saveName=${instance.saveName}", it)
                }
            )
            logger.debug { "Successfully saved PluginData: ${instance.saveName} (containing ${instance.valueNodes.size} properties)" }
        }
    }
}