package xyz.cssxsh.mirai.plugin.tools

import kotlinx.coroutines.*
import xyz.cssxsh.baidu.*
import xyz.cssxsh.baidu.exception.*
import xyz.cssxsh.mirai.plugin.data.*
import java.time.*

object BaiduNetDiskUpdater : BaiduNetDiskClient(config = NetdiskOauthConfig) {

    override val accessToken: String
        get() {
            return try {
                super.accessToken
            } catch (cause: NotTokenException) {
                check(refreshTokenValue.isNotBlank()) { "请使用使用 /backup auth 指令绑定百度云账户" }
                runBlocking {
                    refresh().accessToken
                }
            }
        }

    override var expires: OffsetDateTime by PixivConfigData::netdiskExpiresTime

    override var accessTokenValue: String by PixivConfigData::netdiskAccessToken

    override var refreshTokenValue: String by PixivConfigData::netdiskRefreshToken
}