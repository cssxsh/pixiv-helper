package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMuteAllEvent
import net.mamoe.mirai.event.subscribeAlways
import kotlin.coroutines.CoroutineContext

class PixivHelperListener {

    private val list = mutableListOf<Job>()

    fun listen(): Unit = GlobalEventChannel.parentScope(PixivHelperPlugin).run {
        subscribeAlways<GroupMuteAllEvent> {
            PixivHelperManager.remove(group)
        }.let { list.add(it) }
    }

    fun stop() = list.forEach { it.cancel() }
}