package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import net.mamoe.mirai.event.events.GroupMuteAllEvent
import net.mamoe.mirai.event.subscribeAlways
import kotlin.coroutines.CoroutineContext

class PixivHelperListener(parentCoroutineContext: CoroutineContext): CoroutineScope {

    override val coroutineContext: CoroutineContext by lazy {
        parentCoroutineContext + CoroutineName("PixivHelperListener")
    }

    fun listen() = apply {
        subscribeAlways<GroupMuteAllEvent> {
            PixivHelperManager.remove(group)
        }
    }

    fun stop() = apply {
        this.coroutineContext[Job]?.apply { cancel() }
    }
}