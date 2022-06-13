package xyz.cssxsh.mirai.pixiv.data

import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.util.*

public sealed interface PixivHelperConfig : PluginConfig {
    public companion object : Collection<PluginConfig> {
        private val configs by lazy {
            PixivHelperConfig::class.sealedSubclasses.mapNotNull { kClass -> kClass.objectInstance }
        }

        override val size: Int get() = configs.size

        override fun contains(element: PluginConfig): Boolean = configs.contains(element)

        override fun containsAll(elements: Collection<PluginConfig>): Boolean = configs.containsAll(elements)

        override fun isEmpty(): Boolean = configs.isEmpty()

        override fun iterator(): Iterator<PluginConfig> = configs.iterator()

        @OptIn(ConsoleExperimentalApi::class)
        public operator fun get(name: String): PluginConfig = configs.first { it.saveName.equals(name, true) }
    }
}