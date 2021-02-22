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
import xyz.cssxsh.pixiv.api.apps.*
import xyz.cssxsh.pixiv.model.*

object PixivTagCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "tag", "标签",
    description = "PIXIV标签"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private suspend fun PixivHelper.searchTag(
        tag: String,
        limit: Long = 1000,
    ) = buildList {
        (0 until limit step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
            runCatching {
                searchIllust(word = tag, offset = offset).illusts
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                addAll(it)
                logger.verbose { "加载'${tag}'搜索列表第${page}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载'${tag}'搜索列表第${page}页失败" }, it)
            }
        }
    }

    private suspend fun PixivHelper.getRelated(
        pid: Long,
        illusts: List<Long>,
    ) = buildList {
        (0 until AppApi.RELATED_OFFSET step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
            runCatching {
                illustRelated(pid = pid, seedIllustIds = illusts, offset = offset).illusts
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                addAll(it)
                logger.verbose { "加载[${pid}]相关列表第${page}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载[${pid}]相关列表第${page}页失败" }, it)
            }
        }
    }

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
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(tag: String) = getHelper().runCatching {
        check(tag.length <= 30) {
            "标签'$tag'过长"
        }
        useTagInfoMapper { it.findByName(tag) }.apply {
            logger.verbose { "根据TAG: $tag 在缓存中找到${size}个作品" }
        }.let { list ->
            if (list.size < PixivHelperSettings.eroInterval) addCacheJob(name = "SEARCH(${tag})", reply = false) {
                searchTag(tag).filter { illust ->
                    illust.isEro()
                }.also { list ->
                    logger.verbose { "'${tag}'共搜索到${list.size}个作品" }
                }
            }
            list.random().also { pid ->
                if (list.size < PixivHelperSettings.eroInterval) addCacheJob(name = "RELATED(${pid})", reply = false) {
                    getRelated(pid, list).filter { illust ->
                        illust.isEro()
                    }.also { list ->
                        logger.verbose { "[${pid}]相关共获取到${list.size}个作品" }
                    }
                }
                tagStatisticAdd(event = fromEvent, tag = tag, pid = pid)
            }.let { pid ->
                buildMessageByIllust(pid)
            }
        }
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        tagStatisticAdd(event = fromEvent, tag = tag, pid = null)
        quoteReply("读取色图失败, 标签为PIXIV用户添加的标签, 请尝试日文或英文 ${it.message}")
    }.isSuccess
}