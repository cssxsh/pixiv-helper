package xyz.cssxsh.mirai.plugin.tools

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import xyz.cssxsh.baidu.*
import xyz.cssxsh.baidu.exption.NotTokenException
import xyz.cssxsh.baidu.oauth.*
import xyz.cssxsh.mirai.plugin.data.*
import java.time.*

object BaiduNetDiskUpdater : BaiduNetDiskClient(config = NetdiskOauthConfig) {

    override val accessToken: String
        get() {
            return try {
                super.accessToken
            } catch (cause: NotTokenException) {
                check(refreshTokenValue.isNullOrBlank()) { "请使用使用 /backup auth 指令绑定百度云账户" }
                runBlocking {
                    cause.client.refresh().accessToken
                }
            }
        }

    override suspend fun saveToken(token: AuthorizeAccessToken) {
        super.saveToken(token)
        PixivConfigData.netdiskAccessToken = accessTokenValue.orEmpty()
        PixivConfigData.netdiskRefreshToken = refreshTokenValue.orEmpty()
        PixivConfigData.netdiskExpires = expires.toEpochSecond()
    }

    suspend fun loadToken(): Unit = mutex.withLock {
        accessTokenValue = PixivConfigData.netdiskAccessToken
        refreshTokenValue = PixivConfigData.netdiskRefreshToken
        expires = OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(PixivConfigData.netdiskExpires),
            ZoneId.systemDefault()
        )
    }
}