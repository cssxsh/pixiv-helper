package xyz.cssxsh.mirai.plugin

import io.ktor.client.request.*
import io.ktor.http.*
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
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger

object PixivHelperListener {

    private val list = mutableListOf<Listener<*>>()

    private fun Listener<*>.addToList() = list.add(this)

    fun subscribe(): Unit = GlobalEventChannel.parentScope(PixivHelperPlugin).run {
        subscribeAlways<GroupMuteAllEvent> {
            PixivHelperManager.remove(group)
        }.addToList()
        subscribeMessages {
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
        }.addToList()
    }

    fun stop() = list.forEach { it.cancel() }

    /**
     * https://www.pixiv.net/artworks/79695391
     * https://www.pixiv.net/member_illust.php?mode=medium&illust_id=82876433
     */
    private val URL_ARTWORK_REGEX =
        """(?<=(artworks/|illust_id=))\d+""".toRegex()

    /**
     * https://www.pixiv.net/users/902077
     * http://www.pixiv.net/member.php?id=902077
     */
    private val URL_USER_REGEX =
        """(?<=(users/|member\.php\?id=))\d+""".toRegex()

    /**
     * [https://www.pixiv.net/info.php?id=1554]
     * https://pixiv.me/milkpanda-yellow
     */
    private val URL_PIXIV_ME_REGEX =
        """(?<=pixiv\.me/)[0-9a-z_-]{3,32}""".toRegex()

    private suspend fun MessageEvent.sendArtworkInfo(pid: Long): MessageReceipt<Contact> = getHelper().run {
        subject.sendMessage(message.quote() + buildMessageByIllust(pid = pid).toMessageChain())
    }

    private suspend fun MessageEvent.sendUserInfo(uid: Long): MessageReceipt<Contact> = getHelper().run {
        subject.sendMessage(message.quote() + buildMessageByUser(uid = uid))
    }

    private suspend fun MessageEvent.sendUserInfo(account: String): MessageReceipt<Contact> = getHelper().run {
        useHttpClient { client ->
            client.head<HttpMessage>("https://pixiv.me/$account").headers[HttpHeaders.Location]
        }?.let { location ->
            URL_USER_REGEX.find(location)?.value?.toLong()
        }?.let { uid ->
            sendUserInfo(uid = uid)
        } ?: subject.sendMessage(message.quote() + "跳转失败, https://pixiv.me/$account")
    }
}