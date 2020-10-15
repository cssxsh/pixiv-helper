package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.GroupMuteAllEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.event.subscribeAlways
import kotlin.coroutines.CoroutineContext

class PixivHelperListener(private val parentCoroutineContext: CoroutineContext) {
    private var _coroutineScope: CoroutineScope? = null

    private val coroutineScope: CoroutineScope
        get() = _coroutineScope?.takeIf {
            it.isActive
        } ?: CoroutineScope(parentCoroutineContext + CoroutineName("PixivHelperListener")).also {
            _coroutineScope = it
        }

    fun start() = coroutineScope.apply {
        subscribeAlways<NewFriendRequestEvent> {
            accept()
        }
        subscribeAlways<BotInvitedJoinGroupRequestEvent> {
            accept()
        }
        subscribeAlways<GroupMuteAllEvent> {
            PixivHelperManager.remove(group)
        }
    }

    fun stop(): CoroutineScope? = _coroutineScope?.apply {
        cancel()
    }
}