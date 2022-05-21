package xyz.cssxsh.mirai.pixiv.command

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.mirai.pixiv.model.*

object PixivTagCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "tag", "标签", "[饥饿]",
    description = "PIXIV标签"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    private fun CommandSenderOnMessage<*>.record(tag: String, pid: Long?) = launch(SupervisorJob()) {
        StatisticTagInfo(
            sender = fromEvent.sender.id,
            group = (fromEvent.subject as? Group)?.id,
            pid = pid,
            tag = tag,
            timestamp = fromEvent.time.toLong()
        ).replicate()
    }

    private val jobs: MutableSet<String> = HashSet()

    @Handler
    suspend fun CommandSenderOnMessage<*>.tag(word: String, bookmark: Long = 0, fuzzy: Boolean = false) = withHelper {
        if (word in jobs) return@withHelper "TAG指令缓存中"
        val list = ArtWorkInfo.tag(word = word, marks = bookmark, fuzzy = fuzzy, age = TagAgeLimit, limit = EroChunk)
        logger.verbose { "根据TAG: $word 在缓存中找到${list.size}个作品" }
        val artwork = list.randomOrNull()

        PixivEroCommand += list
        if (list.size < EroChunk && jobs.add(word)) {
            addCacheJob(name = "TAG[${word}]", reply = false) {
                getSearchTag(tag = word).eros().onCompletion {
                    jobs.remove(word)
                }
            }
        }

        record(tag = word, pid = artwork?.pid)

        if (artwork != null) {
            if (list.size < EroChunk && jobs.add(artwork.pid.toString())) {
                addCacheJob(name = "RELATED(${artwork.pid})", reply = false) {
                    getRelated(pid = artwork.pid).eros().onCompletion {
                        jobs.remove(artwork.pid.toString())
                    }
                }
            }
            artwork
        } else {
            if (fuzzy) {
                "$subject 读取Tag[${word}]色图失败, 标签为PIXIV用户添加的标签, 请尝试日文或英文"
            } else {
                "$subject 读取Tag[${word}]色图失败, 请尝试模糊搜索或日文和英文"
            }
        }
    }
}