package xyz.cssxsh.mirai.pixiv

import kotlinx.coroutines.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.utils.*
import kotlin.coroutines.*
import kotlin.properties.*
import kotlin.reflect.*

public object PixivHelperPool : ReadOnlyProperty<Contact, PixivHelper>, CoroutineScope {
    private val logger by lazy { MiraiLogger.Factory.create(this::class, identity = "pixiv-helper-pool") }

    override val coroutineContext: CoroutineContext =
        CoroutineName(name = "pixiv-helper-pool") + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
            logger.warning({ "$throwable in $context" }, throwable)
        }

    private val helpers: MutableMap<Long, PixivHelper> = java.util.concurrent.ConcurrentHashMap()

    override fun getValue(thisRef: Contact, property: KProperty<*>): PixivHelper {
        return helper(contact = thisRef)
    }

    @Synchronized
    public fun helper(contact: Contact): PixivHelper {
        return helpers.getOrPut(contact.id) {
            PixivHelper(id = contact.id, parentCoroutineContext = coroutineContext)
        }
    }
}