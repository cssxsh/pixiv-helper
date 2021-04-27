package xyz.cssxsh.mirai.plugin.tools

import net.mamoe.mirai.utils.*
import xyz.cssxsh.baidu.BaiduNetDiskClient
import xyz.cssxsh.baidu.oauth.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import java.time.*

object BaiduNetDiskUpdater : BaiduNetDiskClient(
    appName = "PixivHelper",
    appId = 23705658,
    appKey = "Ocyz2NgvSGcnZyRs7con1dQNeHPKzBd2",
    secretKey = "xZVvfiGNPleBbSixDaSnFbrnIux7OWmd"
) {

    override val accessToken: String get() = runCatching { super.accessToken }.onFailure {
        logger.warning {
            "认证失效, 请访问以下网址，使用/backup auth <code> 输入code, ${getWebAuthorizeUrl(AuthorizeType.AUTHORIZATION)} "
        }
    }.getOrThrow()

    override fun saveToken(token: AuthorizeAccessToken) {
        super.saveToken(token)
        PixivConfigData.netdiskAccessToken = accessTokenValue.orEmpty()
        PixivConfigData.netdiskRefreshToken = refreshTokenValue.orEmpty()
        PixivConfigData.netdiskExpires = expires.toEpochSecond()
    }

    public override var expires: OffsetDateTime
        get() = super.expires
        set(value) { super.expires = value }

    fun loadToken(): Unit = synchronized(expires) {
        accessTokenValue = PixivConfigData.netdiskAccessToken
        refreshTokenValue = PixivConfigData.netdiskRefreshToken
        expires = OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(PixivConfigData.netdiskExpires),
            ZoneId.systemDefault()
        )
    }
}