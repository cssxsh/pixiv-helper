package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.command.PixivTagCommand.searchTag
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.pixiv.api.app.AppApi
import xyz.cssxsh.pixiv.api.app.illustRelated
import xyz.cssxsh.pixiv.api.app.searchIllust
import xyz.cssxsh.pixiv.data.app.IllustInfo

object PixivTagCommand: SimpleCommand(
    PixivHelperPlugin,
    "tag", "标签",
    description = "pixiv 标签",
    prefixOptional = true
), PixivHelperLogger {

    private val jobs : MutableList<Job> = mutableListOf()

    private fun PixivHelper.searchTag(tag: String, limit: Long = 100) = launch {
        buildList {
            (0 until limit step AppApi.PAGE_SIZE).forEach { offset ->
                runCatching {
                    searchIllust(word = tag, offset = offset).illusts
                }.onSuccess {
                    if (it.isEmpty()) return@buildList
                    add(PixivCacheData.filter(it).values)
                    logger.verbose("加载搜索列表第${offset / 30}页{${it.size}}成功")
                }.onFailure {
                    logger.verbose("加载搜索列表第${offset / 30}页失败", it)
                }
            }
        }.flatten().filter {
            it.isEro()
        }.also {
            logger.verbose("共搜索到${it.size}个作品")
        }.runCatching {
            forEach {
                getImages(it)
                addRelated(illust = it, this)
            }
        }
    }.also {
        jobs.add(it)
    }

    private fun PixivHelper.addRelated(illust: IllustInfo, illusts: List<IllustInfo>, limit: Long = 100) = launch {
        val seeds: List<Long> = (0 until 10).map { illusts.random().pid }
        buildList {
            (0 until limit step AppApi.PAGE_SIZE).forEach { offset ->
                runCatching {
                    illustRelated(
                        pid = illust.pid,
                        seedIllustIds = seeds,
                        offset = offset
                    ).illusts
                }.onSuccess {
                    if (it.isEmpty()) return@buildList
                    add(PixivCacheData.filter(it).values)
                    logger.verbose("加载相关列表第${offset / 30}页{${it.size}}成功")
                }.onFailure {
                    logger.verbose("加载相关列表第${offset / 30}页失败", it)
                }
            }
        }.flatten().filter {
            it.isEro()
        }.also {
            logger.verbose("共搜索到${it.size}个作品")
        }.forEach {
            getImages(it)
        }
    }.also {
        jobs.add(it)
    }

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(tag: String) = getHelper().runCatching {
        PixivCacheData.eros.values.filter { illust ->
            tag in illust.title || illust.tags.any { tag in it.name || tag in it.translatedName ?: "" }
        }.let { list ->
            logger.verbose("根据TAG: $tag 在涩图中找到${list.size}个作品")
            buildMessage(list.random().also { addRelated(it, list) })
        }
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("读取色图失败， ${it.message}")
        getHelper().searchTag(tag)
    }.isSuccess
}