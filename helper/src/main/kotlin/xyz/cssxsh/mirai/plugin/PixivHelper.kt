@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings.delayTime
import xyz.cssxsh.pixiv.client.*
import xyz.cssxsh.pixiv.data.AuthInfoDelegate
import xyz.cssxsh.pixiv.data.AuthResult
import xyz.cssxsh.pixiv.data.ConfigDelegate
import xyz.cssxsh.pixiv.data.ExpiresTimeDelegate
import xyz.cssxsh.pixiv.data.app.IllustInfo
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

/**
 * 助手实例
 */
class PixivHelper(val contact: Contact) : SimplePixivClient(
    parentCoroutineContext = PixivHelperPlugin.coroutineContext,
    coroutineName = "PixivHelper:${contact}",
    config = PixivConfig()
) {

    override var config: PixivConfig by ConfigDelegate(contact)

    override var authInfo: AuthResult.AuthInfo? by AuthInfoDelegate(contact)

    public override var expiresTime: OffsetDateTime by ExpiresTimeDelegate(contact)

    val historyQueue by lazy {
        ArrayBlockingQueue<Long>(PixivHelperSettings.minInterval)
    }

    var minSanityLevel = 1
        set(value) {
            field = minOf(value, 6)
        }

    var minBookmarks: Long = 0

    var simpleInfo: Boolean
        get() = PixivConfigData.simpleInfo.getOrPut(contact.toString()) { contact !is Friend }
        set(value) {
            PixivConfigData.simpleInfo[contact.toString()] = value
        }

    private var cacheJob: Job? = null

    private val cacheList: MutableList<Pair<String, suspend PixivHelper.() -> List<IllustInfo>>> = mutableListOf()

    private suspend fun loadCache(name: String, list: List<IllustInfo>) {
        list.sortedBy { it.pid }.takeIf { it.isNotEmpty() }?.apply {
            logger.verbose { "任务<$name>共${size}个作品信息将会被尝试缓存" }
            runCatching {
                (list.map { it.pid } - useArtWorkInfoMapper { it.keys(list.first().pid..list.last().pid) }).let { keys ->
                    reply("任务<$name>有{${keys.first()}...${keys.last()}}共${size}个新作品等待缓存")
                }
            }
            runCatching {
                size to filter { illust ->
                    isActive && illust.runCatching {
                        getImages(pid, getOriginUrl())
                    }.onSuccess {
                        delay(delayTime)
                    }.onFailure {
                        logger.warning({ "任务<$name>获取作品(${illust.pid})[${illust.title}]错误" }, it)
                        runCatching {
                            reply("任务<$name>获取作品(${illust.pid})[${illust.title}]错误, ${it.message}")
                        }
                    }.isSuccess
                }.apply { saveToSQLite() }.size
            }.onSuccess { (total, success) ->
                logger.verbose { "任务<$name>缓存完毕, 共${total}个新作品, 缓存成功${success}个" }
                runCatching {
                    reply("任务<$name>缓存完毕, 共${total}个新作品, 缓存成功${success}个")
                }
            }.onFailure {
                logger.warning({ "任务<$name>缓存失败" }, it)
                runCatching {
                    reply("任务<$name>缓存失败, ${it.message}")
                }
            }
        }
    }

    fun addCacheJob(name: String, write: Boolean = true, block: suspend PixivHelper.() -> List<IllustInfo>): Boolean =
        cacheList.add(name to block).also {
            logger.verbose { "任务<$name>已添加" }
            if (cacheJob?.takeIf { it.isActive } == null) {
                cacheJob = launch(Dispatchers.IO) {
                    while (isActive && cacheList.isNotEmpty()) {
                        cacheList.removeFirst().let { (name, getIllusts) ->
                            getIllusts.invoke(this@PixivHelper).sortedBy { it.pid }.let { list ->
                                if (write) list.writeToCache()
                                loadCache(name, list)
                            }
                        }
                    }
                }
            }
        }

    suspend fun cacheStop() =
        cacheJob?.apply {
            cacheList.clear()
            cancelAndJoin()
        }

    var tagJob: Job? = null

    var followJob: Job? = null

    override fun config(block: PixivConfig.() -> Unit) =
        config.apply(block).also { config = it }

    override suspend fun refresh(token: String) = super.refresh(token).also {
        logger.info { "$it by RefreshToken: $token, ExpiresTime: $expiresTime" }
    }

    override suspend fun login(mailOrPixivID: String, password: String) = super.login(mailOrPixivID, password).also {
        logger.info { "$it by Account: $mailOrPixivID, ExpiresTime: $expiresTime" }
    }

    /**
     * 给这个助手的联系人发送消息
     */
    @JvmSynthetic
    suspend fun reply(message: Message): MessageReceipt<Contact> =
        contact.sendMessage(message)

    /**
     * 给这个助手的联系人发送文本消息
     */
    @JvmSynthetic
    suspend fun reply(plain: String): MessageReceipt<Contact> =
        contact.sendMessage(PlainText(plain))
}