@file:Suppress("unused")
package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.FriendCommandSenderOnMessage
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.console.command.TempCommandSenderOnMessage
import xyz.cssxsh.mirai.plugin.PixivHelper
import xyz.cssxsh.mirai.plugin.PixivHelperStorage

/**
 * 从好友私聊 获取对应的助手
 */
fun FriendCommandSenderOnMessage.getHelper() = PixivHelperStorage.getOrPut(user) { PixivHelper(subject) }

/**
 * 从临时私聊 获取对应的助手
 */
fun TempCommandSenderOnMessage.getHelper() = PixivHelperStorage.getOrPut(user) { PixivHelper(subject) }

/**
 * 从群聊 获取对应的助手
 */
fun MemberCommandSenderOnMessage.getHelper() = PixivHelperStorage.getOrPut(group) { PixivHelper(subject) }