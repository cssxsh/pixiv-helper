package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.flow.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*

object PixivBoomCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "boom", "射爆", "社保",
    description = "PIXIV色图爆炸指令"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    @Handler
    suspend fun CommandSenderOnMessage<*>.handle(limit: Int = EroChunk, word: String = "") = withHelper {
        val artworks = when {
            word.isEmpty() -> {
                ArtWorkInfo.random(level = 0, marks = 0, age = EroAgeLimit, limit = limit)

            }
            RankMode.values().any { it.name == word.uppercase() } -> {
                val mode = RankMode.valueOf(word.uppercase())
                val flow = getRank(mode = mode)
                addCacheJob(name = "RANK[${mode.name}](new)", reply = false) { flow }

                flow.map { list -> list.map { illust -> illust.toArtWorkInfo() } }.toList().flatten().take(limit)
            }
            word.toLongOrNull() != null -> {
                ArtWorkInfo.user(uid = word.toLong()).sortedByDescending { it.pid }.take(limit)
            }
            else -> {
                ArtWorkInfo.tag(word = word, marks = EroStandard.marks, fuzzy = false, age = TagAgeLimit, limit = limit)
            }
        }

        if (artworks.isEmpty()) return@withHelper "列表为空".toPlainText()

        PixivEroCommand += artworks

        val list = mutableListOf<ForwardMessage.Node>()

        sendMessage("开始将${artworks.size}个作品合成转发消息，请稍后...")

        for (artwork in artworks.sortedBy { it.pid }) {
            val sender = (subject as? User) ?: (subject as Group).members.random()

            try {
                val illust = getIllustInfo(pid = artwork.pid, flush = false)
                list.add(
                    ForwardMessage.Node(
                        senderId = sender.id,
                        senderName = sender.nameCardOrNick,
                        time = illust.createAt.toEpochSecond().toInt(),
                        message = buildMessageByIllust(illust)
                    )
                )
            } catch (e: Throwable) {
                list.add(
                    ForwardMessage.Node(
                        senderId = sender.id,
                        senderName = sender.nameCardOrNick,
                        time = artwork.created.toInt(),
                        message = "[${artwork.pid}]构建失败 ${e.message}".toPlainText()
                    )
                )
                logger.warning { "BOOM BUILD 错误 $e" }
            }
        }

        RawForwardMessage(list).render(TitleDisplayStrategy("${word.ifEmpty { "随机的" }}的快递"))
    }
}