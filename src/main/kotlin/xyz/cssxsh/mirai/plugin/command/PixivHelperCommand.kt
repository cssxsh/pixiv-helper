package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*

sealed interface PixivHelperCommand : Command {

    companion object : Collection<PixivHelperCommand> {
        private val commands by lazy {
            PixivHelperCommand::class.sealedSubclasses.mapNotNull { kClass -> kClass.objectInstance }
        }

        override val size: Int get() = commands.size

        override fun contains(element: PixivHelperCommand): Boolean = commands.contains(element)

        override fun containsAll(elements: Collection<PixivHelperCommand>): Boolean = commands.containsAll(elements)

        override fun isEmpty(): Boolean = commands.isEmpty()

        override fun iterator(): Iterator<PixivHelperCommand> = commands.iterator()

        operator fun get(name: String): PixivHelperCommand = commands.first { it.primaryName.equals(name, true) }
    }
}