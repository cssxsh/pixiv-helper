package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.api.apps.*

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

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(tag: String) = getHelper().runCatching {
        PixivStatisticalData.tagAdd(user = fromEvent.sender, tag = tag.trim()).also {
            logger.verbose { "${fromEvent.sender}第${it}次使用tag 检索'${tag.trim()}'" }
        }
        useTagInfoMapper { it.findByName(tag) }.apply {
            logger.verbose { "根据TAG: $tag 在缓存中找到${size}个作品" }
        }.let { list ->
            if (list.size < PixivHelperSettings.minInterval) addCacheJob(name = "SEARCH(${tag})", reply = false) {
                searchTag(tag).filter { illust ->
                    illust.isEro()
                }.also { list ->
                    logger.verbose { "'${tag}'共搜索到${list.size}个作品" }
                }
            }
            list.random().let { pid ->
                if (list.size < PixivHelperSettings.minInterval) addCacheJob(name = "RELATED(${pid})", reply = false) {
                    getRelated(pid, list).filter { illust ->
                        illust.isEro()
                    }.also { list ->
                        logger.verbose { "[${pid}]相关共获取到${list.size}个作品" }
                    }
                }
                buildMessageByIllust(pid)
            }

        }
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("读取色图失败, 标签为PIXIV用户添加的标签, 请尝试日文或英文 ${it.message}")
    }.isSuccess
}