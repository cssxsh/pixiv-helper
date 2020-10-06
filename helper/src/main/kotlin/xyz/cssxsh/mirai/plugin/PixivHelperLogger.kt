package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.utils.MiraiLogger

interface PixivHelperLogger {
    val logger: MiraiLogger
        get() = PixivHelperPlugin.logger
}