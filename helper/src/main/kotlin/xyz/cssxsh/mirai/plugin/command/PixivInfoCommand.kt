package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.contact.User
import xyz.cssxsh.mirai.plugin.*

@Suppress("unused")
object PixivInfoCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "info",
    description = "PIXIV信息指令"
) {

    @SubCommand
    @Description("获取助手信息")
    suspend fun CommandSenderOnMessage<*>.helper() = getHelper().runCatching {
        buildString {
            appendLine("Id: ${getAuthInfo().user.uid}")
            appendLine("Name: ${getAuthInfo().user.name}")
            appendLine("Account: ${getAuthInfo().user.account}")
            appendLine("Token: ${getAuthInfo().accessToken}")
            appendLine("ExpiresTime: $expiresTime")
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    @SubCommand
    @Description("获取用户信息")
    suspend fun CommandSenderOnMessage<*>.user(target: User) = runCatching {
        buildString {
            useMappers {
                appendLine("用户: $target")
                appendLine("使用色图指令次数: ${it.statistic.senderEroInfos(target.id)}")
                appendLine("使用标签指令次数: ${it.statistic.senderTagInfos(target.id)}")
            }
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    @SubCommand
    @Description("获取缓存信息")
    suspend fun CommandSenderOnMessage<*>.cache() = runCatching {
        buildString {
            useMappers {
                appendLine("缓存数: ${it.artwork.count()}")
                appendLine("全年龄色图数: ${it.artwork.eroCount()}")
                appendLine("R18色图数: ${it.artwork.r18Count()}")
            }
        }
    }.onSuccess {
        quoteReply(it)
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}