package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.utils.minutesToMillis
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.minInterval
import xyz.cssxsh.pixiv.api.app.*

@Suppress("unused")
object PixivFollowCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "follow",
    description = "PIXIV关注指令"
) {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    private suspend fun PixivHelper.getFollowed(uid: Long, maxNum: Long = 10_000): Set<Long> = buildList {
        (0 until maxNum step AppApi.PAGE_SIZE).forEachIndexed { page, offset ->
            runCatching {
                userFollowing(uid = uid, offset = offset, ignore = apiIgnore).userPreviews.map { it.user.id }
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                add(it)
                logger.verbose { "加载(${uid})关注用户预览第${page}页{${it.size}}成功" }
            }.onFailure {
                logger.warning({ "加载(${uid})关注用户预览第${page}页失败" }, it)
            }
        }
    }.flatten().toSet()

    /**
     * 关注色图作者
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.ero() = getHelper().runCatching {
        check(followJob?.isActive != true) { "正在关注中, ${followJob}..." }
        launch(Dispatchers.IO) {
            val followed = getFollowed(uid = getAuthInfo().user.uid)
            useArtWorkInfoMapper { it.userEroCount() }.filter { (_, count) ->
                count > minInterval
            }.keys.let {
                logger.verbose { "共统计了${it.size}名画师" }
                it - followed
            }.sorted().also {
                logger.info { "用户(${getAuthInfo().user.uid})已关注${followed.size}, 共有${it.size}个用户等待关注" }
                it.runCatching {
                    reply("{${first()}...${last()}}共${size}个画师等待关注")
                }
            }.runCatching {
                var num = 0
                size to count { uid ->
                    isActive && runCatching {
                        userFollowAdd(uid = uid, ignore = apiIgnore)
                    }.onSuccess {
                        logger.info { "用户(${getAuthInfo().user.uid})添加关注(${uid})成功, $it" }
                        if (num >= 8) {
                            logger.verbose { "用户(${getAuthInfo().user.uid})尝试添加关注达到${num}次，将开始延时" }
                            delay(1.minutesToMillis)
                            num = 0
                        } else {
                            num++
                        }
                    }.onFailure {
                        logger.warning({ "用户(${getAuthInfo().user.uid})添加关注(${uid})失败, 将开始延时" }, it)
                        delay(3.minutesToMillis)
                    }.isSuccess
                }
            }.onSuccess { (total, success) ->
                reply("关注完毕共${total}个画师, 关注成功${success}个")
            }.onFailure {
                reply("关注失败, ${it.message}")
            }
        }.also {
            followJob = it
        }
    }.onSuccess { job ->
        quoteReply("关注任务添加完成${job}")
    }.onFailure {
        quoteReply("关注添加失败， ${it.message}")
    }.isSuccess
}