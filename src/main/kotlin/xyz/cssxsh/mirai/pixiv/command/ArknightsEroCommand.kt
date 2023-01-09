package xyz.cssxsh.mirai.pixiv.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.warning
import xyz.cssxsh.mirai.arknights.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.pixiv.*
import java.util.*

public object ArknightsEroCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "ark-ero", "方舟色图",
    description = "PIXIV色图指令"
) {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    private val cache: MutableMap<Long, ArtWorkInfo> = WeakHashMap()

    private fun push(): List<ArtWorkInfo> {
        val list = ArtWorkInfo.tag(word = "Arknights", marks = 10_000L, fuzzy = false, age = AgeLimit.ALL, limit = 100)
        for (artwork in list) {
            cache[artwork.pid] = artwork
        }

        return list
    }

    @Handler
    @Suppress("INVISIBLE_MEMBER")
    public suspend fun UserCommandSender.handle() {
        try {
            if (user.coin > 6_000) {
                user.coin -= 6_000

                val info = if (cache.isEmpty()) {
                    val list = push()
                    list.random()
                } else {
                    cache.values.random()
                }
                cache.remove(info.pid)

                if (cache.size < 30) {
                    launch {
                        push()
                    }
                }

                sendArtwork(info)
            } else {
                sendMessage("合成玉不足, 可以通过答题获取")
            }
        } catch (_: NoClassDefFoundError) {
            logger.warning { "请安装 https://github.com/cssxsh/arknights-helper" }
        }
    }
}