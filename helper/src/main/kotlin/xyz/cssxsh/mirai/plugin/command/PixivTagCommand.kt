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
    "tag", "标签", "[饥饿]",
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

    private val jobs = mutableSetOf<String>()

    internal val cooling = mutableMapOf<Long, Long>().withDefault { 0 }

    @Handler
    suspend fun CommandSenderOnMessage<*>.tag(word: String, bookmark: Long = 0, fuzzy: Boolean = false) = withHelper {
        if (cooling.getValue(contact.id) > System.currentTimeMillis()) {
            val wait = (cooling.getValue(contact.id) - System.currentTimeMillis()) / 1_000
            return@withHelper "TAG指令冷却中, ${wait}s"
        }
        val list = ArtWorkInfo.tag(word = word, marks = bookmark, fuzzy = fuzzy, age = TagAgeLimit, limit = EroChunk)
        logger.verbose { "根据TAG: $word 在缓存中找到${list.size}个作品" }
        val artwork = list.randomOrNull()

        PixivEroCommand += list
        var job = "TAG[${word}]"
        if (list.size < EroChunk && jobs.add(job)) {
            addCacheJob(name = job, reply = false) {
                getSearchTag(tag = word).eros().onCompletion {
                    jobs.remove(job)
                }
            }
        }

        if (artwork != null) {
            job = "RELATED(${artwork.pid})"
            if (list.size < EroChunk && jobs.add(job)) {
                addCacheJob(name = job, reply = false) {
                    getRelated(pid = artwork.pid).eros().onCompletion {
                        jobs.remove(job)
                    }
                }
            }
        }

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