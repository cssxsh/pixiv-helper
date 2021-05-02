package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.Listener
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*

object PixivHelperListener {

    private val listeners = mutableMapOf<String, Listener<*>>()

    internal val images = mutableMapOf<MessageSourceMetadata, Image>()

    private infix fun String.with(listener: Listener<*>) =  synchronized(listeners) {
        listeners.put(this, listener)?.cancel()
    }

    internal fun subscribe(): Unit = GlobalEventChannel.parentScope(PixivHelperPlugin).run {
        "PixivUrl" with subscribeMessages {
            URL_ARTWORK_REGEX finding { result ->
                logger.info { "匹配ARTWORK(${result.value})" }
                sendArtworkInfo(pid = result.value.toLong())
            }
            URL_USER_REGEX finding { result ->
                logger.info { "匹配USER(${result.value})" }
                sendUserInfo(uid = result.value.toLong())
            }
            URL_PIXIV_ME_REGEX finding { result ->
                logger.info { "匹配USER(${result.value})" }
                sendUserInfo(account = result.value)
            }
        }
        "SearchImage" with subscribeMessages {
            always {
                message.findIsInstance<Image>()?.let { image ->
                    synchronized(images) {
                        images[source.metadata()] = image
                    }
                }
            }
        }
    }

    internal fun stop() = synchronized(listeners) {
        listeners.forEach { (_, listener) ->
            listener.cancel()
        }
        listeners.clear()
    }

    private suspend fun MessageEvent.sendArtworkInfo(pid: Long) = toCommandSender().sendIllust(flush = false) {
        getIllustInfo(pid = pid, flush = false)
    }

    private suspend fun MessageEvent.sendUserInfo(uid: Long) = toCommandSender().withHelper {
        buildMessageByUser(uid = uid)
    }

    private suspend fun MessageEvent.sendUserInfo(account: String) = toCommandSender().withHelper {
        buildMessageByUser(uid = redirect(account = account))
    }
}