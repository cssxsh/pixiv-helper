package xyz.cssxsh.mirai.pixiv

import kotlinx.coroutines.*
import net.mamoe.mirai.event.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.pixiv.data.*
import xyz.cssxsh.mirai.pixiv.event.*
import xyz.cssxsh.mirai.pixiv.model.*
import xyz.cssxsh.mirai.pixiv.task.*
import xyz.cssxsh.pixiv.*
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * Pixiv 助手
 * @see PixivHelperPool.helper
 */
public class PixivHelper internal constructor(public val id: Long, parentCoroutineContext: CoroutineContext) :
    CoroutineScope {

    private val logger by lazy { MiraiLogger.Factory.create(this::class, identity = "pixiv-helper-${id}") }

    override val coroutineContext: CoroutineContext = parentCoroutineContext.childScopeContext("pixiv-helper-${id}")

    public val client: PixivAuthClient by PixivClientPool

    public val uid: Long? get() = (client as? PixivClientPool.AuthClient)?.uid

    public var link: Boolean by LinkDelegate

    public var tag: Boolean by TagDelegate

    public var attr: Boolean by AttrDelegate

    public var max: Int by MaxDelegate

    public var model: SendModel by ModelDelegate

    private var record: Long = 0

    /**
     * @return 返回 true 时取消指令执行
     */
    @Synchronized
    public fun shake(): Boolean {
        val current = System.currentTimeMillis()
        return if (current - record > 3000) {
            record = current
            false
        } else {
            true
        }
    }

    private val eros: MutableMap<Long, ArtWorkInfo> = ConcurrentHashMap()

    /**'
     * 随机一份色图
     */
    public suspend fun ero(sanity: Int, bookmarks: Long): ArtWorkInfo? {
        val event = PixivEvent.EroPost(helper = this, sanity = sanity, bookmarks = bookmarks).broadcast()
        if (event.isCancelled) {
            logger.info { "色图获取被终止" }
            return null
        }

        for ((pid, arkwotk) in eros) {
            if (arkwotk.sanity >= sanity && arkwotk.bookmarks > bookmarks) {
                eros.remove(pid)
                return arkwotk
            }
        }
        for (info in ArtWorkInfo.random(sanity, bookmarks, EroAgeLimit, EroChunk)) {
            eros[info.pid] = info
        }
        for ((pid, arkwotk) in eros) {
            if (arkwotk.sanity >= sanity && arkwotk.bookmarks > bookmarks) {
                eros.remove(pid)
                return arkwotk
            }
        }

        return null
    }

    /**
     * 根据标签获取色图
     */
    public suspend fun tag(word: String, bookmarks: Long, fuzzy: Boolean): ArtWorkInfo? {
        val event = PixivEvent.TagPost(helper = this, word = word, bookmarks = bookmarks, fuzzy = fuzzy).broadcast()
        if (event.isCancelled) {
            logger.info { "标签获取被终止" }
            return null
        }
        val list = ArtWorkInfo.tag(word = word, marks = bookmarks, fuzzy = fuzzy, age = TagAgeLimit, limit = EroChunk)

        for (artwork in list) {
            if (!artwork.ero) continue
            eros[artwork.pid] = artwork
        }
        if (list.size < EroChunk) {
            try {
                PixivCacheLoader.cache(task = buildPixivCacheTask {
                    name = "TAG[${word}]"
                    flow = client.search(tag = word)
                })
            } catch (_: Throwable) {
                //
            }
        }
        return list.randomOrNull()
    }
}