package xyz.cssxsh.mirai.plugin

import io.ktor.client.request.*
import io.ktor.client.statement.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.Listener
import net.mamoe.mirai.event.events.GroupMuteAllEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.*

object PixivHelperListener {

    private val listeners = mutableMapOf<String, Listener<*>>()

    internal val images = mutableMapOf<MessageSource, Image>()

    private infix fun String.with(listener: Listener<*>) = listeners.put(this, listener)?.cancel()

    internal fun subscribe(): Unit = GlobalEventChannel.parentScope(PixivHelperPlugin).run {
        "GroupMuteAll" with subscribeAlways<GroupMuteAllEvent> {
            PixivHelperManager.remove(group)
        }
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
                        images[source] = image
                        images.keys.toList().forEach {
                            if (it.inDuration(SEARCH_EXPIRE).not()) {
                                // 超时删除
                                images.remove(it)
                            }
                        }
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

    private fun MessageEvent.getHelper() = PixivHelperManager[subject]

    private suspend fun MessageEvent.sendArtworkInfo(pid: Long): MessageReceipt<Contact> =
        subject.sendMessage(message.quote() + getHelper().buildMessageByIllust(pid = pid))

    private suspend fun MessageEvent.sendUserInfo(uid: Long): MessageReceipt<Contact> =
        subject.sendMessage(message.quote() + getHelper().buildMessageByUser(uid = uid))

    private suspend fun MessageEvent.sendUserInfo(account: String): MessageReceipt<Contact> = getHelper().run {
        useHttpClient { client ->
            client.head<HttpResponse>("https://pixiv.me/$account").request.url
        }.let { location ->
            URL_USER_REGEX.find(location.encodedPath)
        }?.let { result ->
            sendUserInfo(uid = result.value.toLong())
        } ?: subject.sendMessage(message.quote() + "跳转失败, https://pixiv.me/$account")
    }
}