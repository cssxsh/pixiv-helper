package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.verbose
import net.mamoe.mirai.utils.warning
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperDownloader.getImages
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.mirai.plugin.data.PixivStatisticalData
import xyz.cssxsh.pixiv.api.app.AppApi
import xyz.cssxsh.pixiv.api.app.illustRelated
import xyz.cssxsh.pixiv.api.app.searchIllust

object PixivTagCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "tag", "标签",
    description = "PIXIV标签"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private fun PixivHelper.searchTag(
        tag: String,
        limit: Long = 1000,
    ) = launch(Dispatchers.IO) {
        buildList {
            (0 until limit step AppApi.PAGE_SIZE).forEach { offset ->
                runCatching {
                    searchIllust(word = tag, offset = offset, ignore = apiIgnore).illusts
                }.onSuccess {
                    if (it.isEmpty()) return@buildList
                    add(it)
                    logger.verbose { "加载'${tag}'搜索列表第${offset / 30}页{${it.size}}成功" }
                }.onFailure {
                    logger.warning({ "加载'${tag}'搜索列表第${offset / 30}页失败" }, it)
                }
            }
        }.flatten().filter {
            it.isEro()
        }.also { list ->
            logger.verbose { "'${tag}'共搜索到${list.size}个作品" }
        }.runCatching {
            writeToCache()
            forEach { info ->
                info.apply {
                    getImages(pid, getOriginUrl())
                    saveToSQLite()
                }
            }
        }
    }.also {
        tagJob = it
    }

    private fun PixivHelper.addRelated(
        pid: Long,
        illusts: List<Long>,
        limit: Long = 1000,
    ) = launch(Dispatchers.IO) {
        buildList {
            (0 until limit step AppApi.PAGE_SIZE).forEach { offset ->
                runCatching {
                    illustRelated(pid = pid, seedIllustIds = illusts, offset = offset, ignore = apiIgnore).illusts
                }.onSuccess {
                    if (it.isEmpty()) return@buildList
                    add(it)
                    logger.verbose { "加载[${pid}]相关列表第${offset / 30}页{${it.size}}成功" }
                }.onFailure {
                    logger.warning({ "加载[${pid}]相关列表第${offset / 30}页失败" }, it)
                }
            }
        }.flatten().filter {
            it.isEro()
        }.also { list ->
            logger.verbose { "[${pid}]相关共获取到${list.size}个作品" }
        }.runCatching {
            writeToCache()
            forEach { info ->
                info.apply {
                    getImages(pid, getOriginUrl())
                    saveToSQLite()
                }
            }
        }
    }.also {
        tagJob = it
    }

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle(tag: String) = getHelper().runCatching {
        PixivStatisticalData.tagAdd(user = fromEvent.sender, tag = tag.trim()).also {
            logger.verbose { "${fromEvent.sender}第${it}次使用tag 检索'${tag.trim()}'" }
        }
        if (tagJob?.isActive != true) {
            useTagInfoMapper { it.findByName(tag) }.apply {
                logger.verbose { "根据TAG: $tag 在缓存中找到${size}个作品" }
            }.let { list ->
                list.random().let { pid ->
                    if (list.size < PixivHelperSettings.minInterval) addRelated(pid, list.map { pid })
                    buildMessage(illust = getIllustInfo(pid = pid), save = false)
                }
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