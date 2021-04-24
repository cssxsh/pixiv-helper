package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.contact.*
import xyz.cssxsh.mirai.plugin.data.PixivConfigData
import xyz.cssxsh.pixiv.PixivConfig
import xyz.cssxsh.pixiv.auth.AuthResult
import java.time.OffsetDateTime
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object PixivHelperDelegate {

    val config = ConfigDelegate()

    val auth = AuthInfoDelegate()

    val expires = ExpiresTimeDelegate()

    val link = LinkDelegate()

    private val userAuthInfos: MutableMap<Long, AuthResult?> = mutableMapOf()

    private var defaultAuthInfos: AuthResult? = null

    private val now: OffsetDateTime get() = OffsetDateTime.now().withNano(0)

    private val userExpiresTimes: MutableMap<Long, OffsetDateTime> = mutableMapOf()

    private var defaultExpiresTime: OffsetDateTime = now

    class ConfigDelegate : ReadWriteProperty<PixivHelper, PixivConfig> {

        override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: PixivConfig) {
            when (thisRef.contact) {
                is User -> PixivConfigData.configs[thisRef.contact.id] = value
                is Group -> PixivConfigData.default = value
                else -> throw IllegalAccessException("未知类型联系人!")
            }
        }

        override fun getValue(thisRef: PixivHelper, property: KProperty<*>): PixivConfig = when (thisRef.contact) {
            is User -> PixivConfigData.configs.getOrPut(thisRef.contact.id) { DEFAULT_PIXIV_CONFIG }
            is Group -> PixivConfigData.default
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }

    class AuthInfoDelegate : ReadWriteProperty<PixivHelper, AuthResult?> {

        override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: AuthResult?) {
            when (thisRef.contact) {
                is User -> userAuthInfos[thisRef.contact.id] = value
                is Group -> defaultAuthInfos = value
                else -> throw IllegalAccessException("未知类型联系人!")
            }
        }

        override fun getValue(thisRef: PixivHelper, property: KProperty<*>): AuthResult? = when (thisRef.contact) {
            is User -> userAuthInfos[thisRef.contact.id]
            is Group -> defaultAuthInfos
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }

    class ExpiresTimeDelegate : ReadWriteProperty<PixivHelper, OffsetDateTime> {

        override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: OffsetDateTime) {
            when (thisRef.contact) {
                is User -> userExpiresTimes[thisRef.contact.id] = value
                is Group -> defaultExpiresTime = value
                else -> throw IllegalAccessException("未知类型联系人!")
            }
        }

        override fun getValue(thisRef: PixivHelper, property: KProperty<*>): OffsetDateTime = when (thisRef.contact) {
            is User -> userExpiresTimes.getOrPut(thisRef.contact.id) { now }
            is Group -> defaultExpiresTime
            else -> throw IllegalAccessException("未知类型联系人!")
        }
    }

    class LinkDelegate : ReadWriteProperty<PixivHelper, Boolean> {

        override fun setValue(thisRef: PixivHelper, property: KProperty<*>, value: Boolean) {
            PixivConfigData.link[thisRef.contact.toString()] = value
        }

        override fun getValue(thisRef: PixivHelper, property: KProperty<*>): Boolean =
            PixivConfigData.link.getOrPut(thisRef.contact.toString()) { thisRef.contact !is Group }

    }
}