package xyz.cssxsh.mirai.plugin.permission

import net.mamoe.mirai.console.command.CommandPermission
import net.mamoe.mirai.console.command.CommandSender

object MethodPermission : CommandPermission {
    override fun CommandSender.hasPermission(): Boolean = true
}