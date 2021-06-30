package xyz.cssxsh.mirai.plugin.tools

import net.mamoe.mirai.utils.*
import xyz.cssxsh.baidu.BaiduNetDiskClient
import xyz.cssxsh.baidu.oauth.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import java.time.*

object BaiduNetDiskUpdater : BaiduNetDiskClient(config = NetdiskOauthConfig) {

    override val accessToken: String get() {
        return runCatching {
            super.accessToken
        }.onFailure {
            logger.warning {
                "使用/backup auth 指令进行认证 "
            }
        }.getOrThrow()
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