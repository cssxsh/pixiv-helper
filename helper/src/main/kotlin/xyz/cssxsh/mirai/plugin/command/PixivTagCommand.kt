package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.model.*

@Suppress("unused")
object PixivTagCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "tag", "标签",
    description = "PIXIV标签"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private fun tagStatisticAdd(event: MessageEvent, tag: String, pid: Long?): Boolean = useStatisticInfoMapper { mapper ->
        mapper.replaceTagInfo(StatisticTagInfo(
            sender = event.sender.id,
            group = event.subject.takeIf { it is Group }?.id,
            pid = pid,
            tag = tag,
            timestamp = event.time.toLong()
        ))
    }

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.tag(tag: String) = getHelper().runCatching {
        check(tag.length <= 30) {
            "标签'$tag'过长"
        }
        useTagInfoMapper { it.findByName(tag) }.apply {
            logger.verbose { "根据TAG: $tag 在缓存中找到${size}个作品" }
        }.let { list ->
            if (list.size < PixivHelperSettings.eroInterval) addCacheJob(name = "SEARCH(${tag})", reply = false) {
                searchTag(tag)
            }
            list.random().also { pid ->
                if (list.size < PixivHelperSettings.eroInterval) addCacheJob(name = "RELATED(${pid})", reply = false) {
                    getRelated(pid, list)
                }
                tagStatisticAdd(event = fromEvent, tag = tag, pid = pid)
            }.let { pid ->
                buildMessageByIllust(pid = pid, save = false)
            }
        }
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        tagStatisticAdd(event = fromEvent, tag = tag, pid = null)
        quoteReply("读取色图失败, 标签为PIXIV用户添加的标签, 请尝试日文或英文 ${it.message}")
    }.isSuccess
}