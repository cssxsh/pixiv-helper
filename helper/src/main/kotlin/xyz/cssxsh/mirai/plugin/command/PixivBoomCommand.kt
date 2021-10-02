package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*

object PixivBoomCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "boom", "射爆","社保",
    description = "PIXIV色图爆炸指令"
) {

    override val prefixOptional: Boolean = true

    @Handler
    suspend fun CommandSenderOnMessage<*>.handle(limit: Int = 30, word: String = "") = withHelper {
        val artworks = if (word.isEmpty()) {
            ArtWorkInfo.random(level = 0, marks = 0, age = EroAgeLimit, limit = limit)
        } else {
            val names = word.split(delimiters = TAG_DELIMITERS).filter { it.isNotBlank() }.toTypedArray()
            ArtWorkInfo.tag(names = names, marks = 0, fuzzy = true, age = TagAgeLimit, limit = limit)
        }

        if (artworks.isEmpty()) return@withHelper "列表为空".toPlainText()

        PixivEroCommand += artworks

        val list = mutableListOf<ForwardMessage.Node>()

        sendMessage("开始将${artworks.size}个作品合成转发消息，请稍后...")

        for (artwork in artworks.sortedBy { it.pid }) {
            runCatching {
                val illust = getIllustInfo(pid = artwork.pid, flush = false)

                list.add(
                    ForwardMessage.Node(
                        senderId = fromEvent.sender.id,
                        senderName = fromEvent.sender.nameCardOrNick,
                        time = illust.createAt.toEpochSecond().toInt(),
                        message = buildMessageByIllust(illust)
                    )
                )
            }.onFailure {
                list.add(
                    ForwardMessage.Node(
                        senderId = fromEvent.sender.id,
                        senderName = fromEvent.sender.nameCardOrNick,
                        time = artwork.created.toInt(),
                        message = "[${artwork.pid}]构建失败".toPlainText()
                    )
                )
                logger.warning { "BOOM BUILD 错误 $it" }
            }
        }

        RawForwardMessage(list).render(TitleDisplayStrategy("${word.ifEmpty { "随机的" }}的快递"))
    }
}