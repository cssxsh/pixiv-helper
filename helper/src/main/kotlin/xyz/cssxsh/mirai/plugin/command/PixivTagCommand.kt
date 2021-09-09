package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*

object PixivTagCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "tag", "标签",
    description = "PIXIV标签"
) {

    override val prefixOptional: Boolean = true

    private fun CommandSenderOnMessage<*>.record(tag: String, pid: Long?) = launch(SupervisorJob()) {
        StatisticTagInfo(
            sender = fromEvent.sender.id,
            group = fromEvent.subject.takeIf { it is Group }?.id,
            pid = pid,
            tag = tag,
            timestamp = fromEvent.time.toLong()
        ).replicate()
    }

    private fun tags(word: String, bookmark: Long, fuzzy: Boolean): List<ArtWorkInfo> {
        val direct = ArtWorkInfo.tag(word, marks = bookmark, fuzzy = fuzzy, limit = EroChunk)
        val names = word.split(delimiters = TAG_DELIMITERS).filter { it.isNotBlank() }.toTypedArray()
        val split = ArtWorkInfo.tag(*names, marks = bookmark, fuzzy = fuzzy, limit = EroChunk)

        return (direct + split).distinctBy { it.pid }
    }

    private const val TAG_NAME_MAX = 30

    private val jobs = mutableSetOf<String>()

    private val cooling = mutableMapOf<Long, Long>()

    @Handler
    suspend fun CommandSenderOnMessage<*>.tag(word: String, bookmark: Long = 0, fuzzy: Boolean = false) = withHelper {
        if ((cooling[contact.id] ?: 0) > System.currentTimeMillis()) return@withHelper "TAG指令冷却中"
        check(word.length <= TAG_NAME_MAX) { "标签'$word'过长" }
        tags(word = word, bookmark = bookmark, fuzzy = fuzzy).let { list ->
            logger.verbose { "根据TAG: $word 在缓存中找到${list.size}个作品" }
            PixivEroCommand += list
            val tagCache = "TAG[${word}]"
            if (list.size < EroChunk && tagCache !in jobs) {
                jobs.add(tagCache)
                addCacheJob(name = tagCache, reply = false) {
                    getSearchTag(tag = word).eros().onCompletion {
                        jobs.remove(tagCache)
                    }
                }
            }
            list.randomOrNull()?.also { artwork ->
                val relatedCache = "RELATED(${artwork.pid})"
                if (list.size < EroChunk && relatedCache !in jobs) {
                    jobs.add(relatedCache)
                    addCacheJob(name = relatedCache, reply = false) {
                        getRelated(pid = artwork.pid, seeds = list.map { it.pid }.toSet()).eros().onCompletion {
                            jobs.remove(relatedCache)
                        }
                    }
                }
            }.let { artwork ->
                record(tag = word, pid = artwork?.pid)
                requireNotNull(artwork) {
                    cooling[contact.id] = System.currentTimeMillis() + TagCooling
                    if (fuzzy) {
                        "$subject 读取Tag[${word}]色图失败, 标签为PIXIV用户添加的标签, 请尝试日文或英文"
                    } else {
                        "$subject 读取Tag[${word}]色图失败, 请尝试模糊搜索或日文和英文"
                    }
                }
            }
        }
    }
}