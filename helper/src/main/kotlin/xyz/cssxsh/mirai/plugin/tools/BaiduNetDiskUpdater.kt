package xyz.cssxsh.mirai.plugin.tools

import xyz.cssxsh.baidu.*
import xyz.cssxsh.baidu.oauth.*
import xyz.cssxsh.mirai.plugin.data.*
import java.time.*

object BaiduNetDiskUpdater : BaiduNetDiskClient(config = NetdiskOauthConfig) {

    override val accessToken: String get() {
        return requireNotNull(accessTokenValue?.takeIf { expires >= OffsetDateTime.now() && it.isNotBlank() }) {
            "请使用使用 /backup auth 指令绑定百度云账户"
        }
    }

    override suspend fun saveToken(token: AuthorizeAccessToken) {
        super.saveToken(token)
        PixivConfigData.netdiskAccessToken = accessTokenValue.orEmpty()
        PixivConfigData.netdiskRefreshToken = refreshTokenValue.orEmpty()
        PixivConfigData.netdiskExpires = expires.toEpochSecond()
    }

    fun loadToken(): Unit = synchronized(expires) {
        accessTokenValue = PixivConfigData.netdiskAccessToken
        refreshTokenValue = PixivConfigData.netdiskRefreshToken
        expires = OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(PixivConfigData.netdiskExpires),
            ZoneId.systemDefault()
        )
    }
}