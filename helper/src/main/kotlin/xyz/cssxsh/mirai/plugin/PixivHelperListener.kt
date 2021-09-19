package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.permission.*
import net.mamoe.mirai.console.permission.PermissionService.Companion.testPermission
import net.mamoe.mirai.event.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*

object PixivHelperListener {

    private val listeners = mutableMapOf<String, Listener<*>>()

    internal val images = mutableMapOf<List<Int>, Image>()

    internal val current = mutableMapOf<Long, List<Int>>()

    private infix fun String.with(listener: Listener<*>) = synchronized(listeners) {
        listeners.put(this, listener)?.cancel()
    }

    internal fun subscribe(channel: EventChannel<*>, url: Permission): Unit = with(channel) {
        "PixivUrl" with subscribeMessages {
            URL_ARTWORK_REGEX finding { result ->
                logger.info { "匹配ARTWORK(${result.value})" }
                toCommandSender().takeIf { url.testPermission(it) }?.sendIllustInfo(pid = result.value.toLong())
            }
            URL_USER_REGEX finding { result ->
                logger.info { "匹配USER(${result.value})" }
                toCommandSender().takeIf { url.testPermission(it) }?.sendUserInfo(uid = result.value.toLong())
            }
            URL_PIXIV_ME_REGEX finding { result ->
                logger.info { "匹配USER(${result.value})" }
                toCommandSender().takeIf { url.testPermission(it) }?.sendUserInfo(account = result.value)
            }
        }
        "SearchImage" with subscribeMessages {
            always {
                (message.findIsInstance<Image>() ?: message.findIsInstance<FlashImage>()?.image)?.let { image ->
                    synchronized(images) {
                        val key = source.key()
                        images[key] = image
                        current[subject.id] = key
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

    private suspend fun CommandSenderOnMessage<*>.sendIllustInfo(pid: Long) = withHelper {
        getIllustInfo(pid = pid, flush = false)
    }

    private suspend fun CommandSenderOnMessage<*>.sendUserInfo(uid: Long) = withHelper {
        buildMessageByUser(uid = uid)
    }

    private suspend fun CommandSenderOnMessage<*>.sendUserInfo(account: String) = withHelper {
        buildMessageByUser(uid = redirect(account = account))
    }
}