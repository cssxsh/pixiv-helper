package xyz.cssxsh.mirai.pixiv

import io.ktor.client.request.*
import io.ktor.http.*
import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.console.command.UserCommandSender
import net.mamoe.mirai.console.permission.*
import net.mamoe.mirai.console.permission.PermissionService.Companion.testPermission
import net.mamoe.mirai.event.*
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.event.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.pixiv.apps.*

public object PixivEventListener : SimpleListenerHost() {
    private val logger by lazy { MiraiLogger.Factory.create(this::class, identity = "pixiv-event-listener") }

    @EventHandler
    public fun PixivEvent.handle() {
        helper
        // TODO: stop send
    }

    public var paserPermission: Permission = Permission.getRootPermission()

    @EventHandler
    public suspend fun MessageEvent.handle() {
        val content = message.findIsInstance<PlainText>()?.content ?: return
        if (this is MessageSyncEvent) return
        val context = toCommandSender() as UserCommandSender
        if (paserPermission.testPermission(context).not()) return
        URL_ARTWORK_REGEX.find(content)?.let { match ->
            logger.info { "匹配ARTWORK(${match.value})" }
            context.withHelper {
                loadIllustInfo(pid = match.value.toLong(), client = client)
            }
        }
        URL_USER_REGEX.find(content)?.let { match ->
            logger.info { "匹配USER(${match.value})" }
            context.withHelper {
                client.userDetail(uid = match.value.toLong())
            }
        }
        URL_PIXIV_ME_REGEX.find(content)?.let { match ->
            logger.info { "匹配USER(${match.value})" }
            context.withHelper {
                val uid = UserBaseInfo.get(account = match.value)?.uid ?: client.useHttpClient { http ->
                    http.head("https://pixiv.me/${match.value}")
                        .headers[HttpHeaders.Location]
                        ?.let { URL_USER_REGEX.find(it) }
                        ?.value
                        ?.toLong()
                }
                if (uid != null) {
                    client.userDetail(uid = uid)
                } else {
                    null
                }
            }
        }
        URL_PIXIVISION_ARTICLE.find(content)?.let { match ->
            logger.info { "匹配ARTICLE(${match.value})" }
            // TODO: paser URL_PIXIVISION_ARTICLE
        }
        URL_FANBOX_CREATOR_REGEX.find(content)?.let { match ->
            logger.info { "匹配FANBOX(${match.value})" }
            // TODO: paser URL_FANBOX_CREATOR_REGEX
        }
        URL_FANBOX_ID_REGEX.find(content)?.let { match ->
            logger.info { "匹配FANBOX(${match.value})" }
            // TODO: paser URL_FANBOX_ID_REGEX
        }
    }
}

//object PixivHelperListener {
//
//    private val listeners: MutableMap<String, Listener<*>> = HashMap()
//
//    private infix fun String.with(listener: Listener<*>) = synchronized(listeners) {
//        listeners.put(this, listener)?.cancel()
//    }
//
//    internal fun subscribe(channel: EventChannel<*>, permission: Permission): Unit = with(channel) {
//        "PixivUrl" with subscribeMessages {
//            URL_ARTWORK_REGEX finding { result ->
//                logger.info { "匹配ARTWORK(${result.value})" }
//                toCommandSender().takeIf { permission.testPermission(it) }?.sendIllustInfo(pid = result.value.toLong())
//            }
//            URL_USER_REGEX finding { result ->
//                logger.info { "匹配USER(${result.value})" }
//                toCommandSender().takeIf { permission.testPermission(it) }?.sendUserInfo(uid = result.value.toLong())
//            }
//            URL_PIXIV_ME_REGEX finding { result ->
//                logger.info { "匹配USER(${result.value})" }
//                toCommandSender().takeIf { permission.testPermission(it) }?.sendUserInfo(account = result.value)
//            }
//            URL_PIXIVISION_ARTICLE finding { result ->
//                logger.info { "匹配ARTICLE(${result.value})" }
//                toCommandSender().takeIf { permission.testPermission(it) }?.sendArticle(aid = result.value.toLong())
//            }
//            URL_FANBOX_CREATOR_REGEX finding { result ->
//                if (result.value == "api" || result.value == "www") return@finding
//                logger.info { "匹配FANBOX(${result.value})" }
//                toCommandSender().takeIf { permission.testPermission(it) }?.sendCreatorInfo(id = result.value)
//            }
//            URL_FANBOX_ID_REGEX finding { result ->
//                logger.info { "匹配FANBOX(${result.value})" }
//                toCommandSender().takeIf { permission.testPermission(it) }?.sendCreatorInfo(uid = result.value.toLong())
//            }
//        }
//    }
//
//    internal fun stop() = synchronized(listeners) {
//        for ((_, listener) in listeners) listener.cancel()
//        listeners.clear()
//    }
//
//    private suspend fun CommandSenderOnMessage<*>.sendIllustInfo(pid: Long) = withHelper {
//        getIllustInfo(pid = pid, flush = false)
//    }
//
//    private suspend fun CommandSenderOnMessage<*>.sendUserInfo(uid: Long) = withHelper {
//        buildMessageByUser(uid = uid)
//    }
//
//    private suspend fun CommandSenderOnMessage<*>.sendUserInfo(account: String) = withHelper {
//        buildMessageByUser(uid = redirect(account = account))
//    }
//
//    private suspend fun CommandSenderOnMessage<*>.sendCreatorInfo(id: String) = withHelper {
//        buildMessageByCreator(creator = creator.get(creatorId = id))
//    }
//
//    private suspend fun CommandSenderOnMessage<*>.sendCreatorInfo(uid: Long) = withHelper {
//        buildMessageByCreator(creator = creator.get(userId = uid))
//    }
//
//    /**
//     */
//    private suspend fun CommandSenderOnMessage<*>.sendArticle(aid: Long) = withHelper {
//        val article = Pixivision.getArticle(aid = aid)
//        val nodes = mutableListOf<ForwardMessage.Node>()
//        getListIllusts(info = article.illusts).collect { illusts ->
//            val list = illusts.map { illust ->
//                val sender = (contact as? User) ?: (contact as Group).members.random()
//                async {
//                    try {
//                        ForwardMessage.Node(
//                            senderId = sender.id,
//                            senderName = sender.nameCardOrNick,
//                            time = illust.createAt.toEpochSecond().toInt(),
//                            message = buildMessageByIllust(illust = illust)
//                        )
//                    } catch (e: Throwable) {
//                        ForwardMessage.Node(
//                            senderId = sender.id,
//                            senderName = sender.nameCardOrNick,
//                            time = illust.createAt.toEpochSecond().toInt(),
//                            message = "[${illust.pid}]构建失败 ${e.message.orEmpty()}".toPlainText()
//                        )
//                    }
//
//                }
//            }.awaitAll()
//
//            nodes.addAll(list)
//        }
//        RawForwardMessage(nodes).render {
//            title = "插画特辑"
//            preview = listOf(article.title) + article.description.lines()
//            summary = "查看特辑的${article.illusts.size}个作品"
//        }
//    }
//}