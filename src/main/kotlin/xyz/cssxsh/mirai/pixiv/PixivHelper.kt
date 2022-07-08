package xyz.cssxsh.mirai.pixiv

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
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

    public val mutex: Mutex = Mutex()

    private var record: Long = 0

    public suspend fun shake(): Boolean {
        return mutex.withLock {
            val current = System.currentTimeMillis()
            if (record + 1000 < current) {
                record = current
                true
            } else {
                false
            }
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
        fun good(sanity: Int, bookmarks: Long): List<ArtWorkInfo> {
            return eros.values.filter { it.sanity >= sanity && it.bookmarks > bookmarks }
        }

        fun random(sanity: Int, bookmarks: Long): ArtWorkInfo? {
            if (good(sanity, bookmarks).isEmpty()) {
                for (info in ArtWorkInfo.random(sanity, bookmarks, EroAgeLimit, EroChunk)) {
                    eros[info.pid] = info
                }
            }
            return good(sanity, bookmarks).randomOrNull()
        }

        return random(sanity = sanity, bookmarks = bookmarks)
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