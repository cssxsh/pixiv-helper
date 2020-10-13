package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.buildMessage
import xyz.cssxsh.mirai.plugin.data.PixivAliasData
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.getHelper

object PixivAliasCommand : CompositeCommand(
    PixivHelperPlugin,
    "Illustrator", "画师",
    description = "缓存指令",
    prefixOptional = true
), PixivHelperLogger {

    @SubCommand("artwork", "作品")
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.artwork(name: String) = getHelper().runCatching {
        requireNotNull(PixivAliasData.aliases[name]) { "找不到别名${name}" }.let { pid ->
            PixivCacheData.caches().values.filter { it.uid == pid }
        }.random().let { info ->
            buildMessage(info)
        }
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    @SubCommand("alias", "别名")
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.alias(name: String, uid: Long) = getHelper().runCatching {
        PixivAliasData.aliases[name] = uid
    }.onSuccess {
        quoteReply("设置 $name -> $uid")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}