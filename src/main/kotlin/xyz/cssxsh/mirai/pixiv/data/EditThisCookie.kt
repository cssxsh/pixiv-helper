package xyz.cssxsh.mirai.pixiv.data

import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.serialization.*

@Serializable
public data class EditThisCookie(
    @SerialName("domain")
    val domain: String,
    @SerialName("expirationDate")
    val expirationDate: Double? = null,
    @SerialName("hostOnly")
    val hostOnly: Boolean,
    @SerialName("httpOnly")
    val httpOnly: Boolean,
    @SerialName("id")
    val id: Int = 0,
    @SerialName("name")
    val name: String,
    @SerialName("path")
    val path: String,
    @SerialName("sameSite")
    val sameSite: String,
    @SerialName("secure")
    val secure: Boolean,
    @SerialName("session")
    val session: Boolean,
    @SerialName("storeId")
    val storeId: String,
    @SerialName("value")
    val value: String
)

public fun EditThisCookie.toCookie(): Cookie = Cookie(
    name = name,
    value = value,
    encoding = CookieEncoding.DQUOTES,
    expires = expirationDate?.run { GMTDate(times(1000).toLong()) },
    domain = domain,
    path = path,
    secure = secure,
    httpOnly = httpOnly
)