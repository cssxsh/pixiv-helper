package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.utils.info

object PixivHelperMain: KotlinPlugin() {
    override fun onLoad() {
        super.onLoad()
    }

    override fun onEnable() {
        super.onEnable()

        logger.info("Plugin loaded!")

        subscribeMessages {
            "greeting" reply { "Hello ${sender.nick}" }
        }

        subscribeAlways<MessageRecallEvent> { event ->
            logger.info { "${event.authorId} 的消息被撤回了" }
        }
    }
}