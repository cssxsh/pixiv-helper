package mirai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.terminal.ConsoleTerminalExperimentalApi
import net.mamoe.mirai.console.terminal.MiraiConsoleImplementationTerminal
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.SilentLogger
import net.mamoe.mirai.utils.debug
import net.mamoe.mirai.utils.warning
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths


@ConsoleExperimentalApi
@ConsoleTerminalExperimentalApi
object RunMirai {

    private fun miraiConsoleImpl(rootPath: Path) = MiraiConsoleImplementationTerminal(
        rootPath = rootPath,
        dataStorageForJvmPluginLoader = JsonPluginDataStorage(rootPath.resolve("data")),
        dataStorageForBuiltIns = JsonPluginDataStorage(rootPath.resolve("data")),
        configStorageForJvmPluginLoader = JsonPluginDataStorage(rootPath.resolve("config")),
        configStorageForBuiltIns = JsonPluginDataStorage(rootPath.resolve("config")),
    )

    @JvmStatic
    fun main(args: Array<String>) {
        // 默认在 /test 目录下运行
        MiraiConsoleTerminalLoader.parse(args, exitProcess = true)
        MiraiConsoleTerminalLoader.startAsDaemon(miraiConsoleImpl(Paths.get(".").toAbsolutePath()))
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
            logger.debug { "Successfully loaded PluginData: ${instance.saveName}" }
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
                    json.encodeToString(instance.updaterSerializer, {}())
                }.getOrElse {
                    throw IllegalStateException("Exception while saving $instance, saveName=${instance.saveName}", it)
                }
            )
            logger.debug { "Successfully saved PluginData: ${instance.saveName}" }
        }
    }
}