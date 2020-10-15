package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.PlainText
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.pixiv.api.app.AppApi
import xyz.cssxsh.pixiv.api.app.illustRelated
import xyz.cssxsh.pixiv.api.app.searchIllust

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
                    add(PixivCacheData.update(it).values)
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
            forEach { info ->
                getImages(info)
                addRelated(pid = info.pid, map { it.pid })
            }
        }
    }.also {
        jobs.add(it)
    }

    private fun PixivHelper.addRelated(pid: Long, illusts: List<Long>, limit: Long = 100) = launch {
        val seeds: List<Long> = (0 until 10).map { illusts.random() }
        buildList {
            (0 until limit step AppApi.PAGE_SIZE).forEach { offset ->
                runCatching {
                    illustRelated(
                        pid = pid,
                        seedIllustIds = seeds,
                        offset = offset
                    ).illusts
                }.onSuccess {
                    if (it.isEmpty()) return@buildList
                    add(PixivCacheData.update(it).values)
                    logger.verbose("加载相关列表第${offset / 30}页{${it.size}}成功")
                }.onFailure {
                    logger.verbose("加载相关列表第${offset / 30}页失败", it)
                }
            }
        }.flatten().filter {
            it.isEro()
        }.also {
            logger.verbose("共获取到${it.size}个相关作品")
        }.forEach {
            getImages(it)
        }
    }.also {
        jobs.add(it)
    }

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(tag: String) = getHelper().runCatching {
        if (jobs.none { it.isActive }) {
            jobs.clear()
            PixivCacheData.caches().values.filter { info ->
                info.isR18().not()
            }.filter { info ->
                 tag in info.title || info.tags.any { tag in it.name || tag in it.translatedName ?: "" }
            }.let { list ->
                logger.verbose("根据TAG: $tag 在缓存中找到${list.size}个作品")
                buildMessage(list.random().also { info ->
                    if (list.size < PixivHelperSettings.maxTagCount) addRelated(info.pid, list.map { it.pid })
                })
            }
        } else {
            listOf(PlainText("技能冷却中"))
        }
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply("读取色图失败, 标签为PIXIV用户添加的标签, 请尝试日文或英文 ${it.message}")
        getHelper().searchTag(tag)
    }.isSuccess
}