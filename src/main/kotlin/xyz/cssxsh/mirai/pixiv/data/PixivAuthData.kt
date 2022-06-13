package xyz.cssxsh.mirai.pixiv.data

import net.mamoe.mirai.console.data.*
import xyz.cssxsh.mirai.pixiv.*
import xyz.cssxsh.pixiv.auth.*
import kotlin.properties.*
import kotlin.reflect.*

public object PixivAuthData : AutoSavePluginData("PixivAuthData"),
    ReadWriteProperty<PixivClientPool.AuthClient, AuthResult?> {

    override fun getValue(thisRef: PixivClientPool.AuthClient, property: KProperty<*>): AuthResult? {
        return results[thisRef.uid]
    }

    override fun setValue(thisRef: PixivClientPool.AuthClient, property: KProperty<*>, value: AuthResult?) {
        if (value == null) {
            results.remove(thisRef.uid)
        } else {
            results[thisRef.uid] = value
        }
    }

    private val results: MutableMap<Long, AuthResult> by value()

    public operator fun plusAssign(auth: AuthResult) {
        results[auth.user.uid] = auth
    }
}