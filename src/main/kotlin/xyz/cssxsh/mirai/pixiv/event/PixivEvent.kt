package xyz.cssxsh.mirai.pixiv.event

import net.mamoe.mirai.event.*
import xyz.cssxsh.mirai.pixiv.*

public sealed interface PixivEvent : Event {
    public val helper: PixivHelper

    /**
     * 色图事件，用来控制色图的发送和触发缓存更新
     * @param helper 上下文
     * @param sanity San值
     * @param bookmarks 收藏数
     * @see xyz.cssxsh.mirai.pixiv.PixivHelper.ero
     */
    public class EroPost(
        public override val helper: PixivHelper,
        public val sanity: Int = 0,
        public val bookmarks: Long = 0
    ) : PixivEvent, CancellableEvent, AbstractEvent()

    /**
     * 标签事件，用来记录和控制色图的发送
     * @param word 关键词
     * @param bookmarks 收藏数
     * @param fuzzy 模糊搜索
     * @see xyz.cssxsh.mirai.pixiv.PixivHelper.tag
     */
    public class TagPost(
        public override val helper: PixivHelper,
        public val word: String,
        public val bookmarks: Long,
        public val fuzzy: Boolean
    ) : PixivEvent, AbstractEvent()
}