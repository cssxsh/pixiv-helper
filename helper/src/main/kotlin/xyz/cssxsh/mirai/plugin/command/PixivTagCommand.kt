package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.flow.onCompletion
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*

object PixivTagCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "tag", "标签",
    description = "PIXIV标签"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private fun tagStatisticAdd(event: MessageEvent, tag: String, pid: Long?): Boolean = useMappers { mappers ->
        mappers.statistic.replaceTagInfo(StatisticTagInfo(
            sender = event.sender.id,
            group = event.subject.takeIf { it is Group }?.id,
            pid = pid,
            tag = tag,
            timestamp = event.time.toLong()
        ))
    }

    private val PERSONA_REGEX = """(.+)[(（](.+)[）)]""".toRegex()

    private fun tags(tag: String, bookmark: Long, fuzzy: Boolean) = useMappers {
        val direct = it.artwork.findByTag(tag, bookmark, fuzzy)
        val persona = PERSONA_REGEX.matchEntire(tag)?.destructured?.let { (character, works) ->
            it.artwork.findByTag(character, bookmark, fuzzy) intersect it.artwork.findByTag(works, bookmark, fuzzy)
        }.orEmpty()

        direct + persona
    }

    private const val TAG_NAME_MAX = 30

    private val jobs = mutableSetOf<String>()

    @Handler
    suspend fun CommandSenderOnMessage<*>.tag(tag: String, bookmark: Long = 0, fuzzy: Boolean = false) = sendIllust {
        check(tag.length <= TAG_NAME_MAX) { "标签'$tag'过长" }
        tags(tag = tag, bookmark = bookmark, fuzzy = fuzzy).let { list ->
            logger.verbose { "根据TAG: $tag 在缓存中找到${list.size}个作品" }
            if (list.size < EroInterval && "TAG(${tag})" !in jobs) {
                jobs.add("TAG(${tag})")
                addCacheJob(name = "TAG(${tag})", reply = false) {
                    getSearchTag(tag = tag).eros().onCompletion {
                        jobs.remove("TAG(${tag})")
                    }
                }
            }
            list.randomOrNull()?.also { artwork ->
                if (list.size < EroInterval && "RELATED(${artwork.pid})" !in jobs) {
                    jobs.add("RELATED(${artwork.pid})")
                    addCacheJob(name = "RELATED(${artwork.pid})", reply = false) {
                        getRelated(pid = artwork.pid, seeds = list.map { it.pid }.toSet()).eros().onCompletion {
                            jobs.remove("RELATED(${artwork.pid})")
                        }
                    }
                }
            }.let { artwork ->
                tagStatisticAdd(event = fromEvent, tag = tag, pid = artwork?.pid)
                requireNotNull(artwork) {
                    if (fuzzy) {
                        "读取Tag(${tag})色图失败, 标签为PIXIV用户添加的标签, 请尝试日文或英文"
                    } else {
                        "读取Tag(${tag})色图失败, 请尝试模糊搜索"
                    }
                }
            }
        }
    }
}