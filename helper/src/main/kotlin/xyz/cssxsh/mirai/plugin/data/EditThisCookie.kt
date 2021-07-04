package xyz.cssxsh.mirai.plugin.data


import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EditThisCookie(
    @SerialName("domain")
    val domain: String,
    @SerialName("expirationDate")
    val expirationDate: Double,
    @SerialName("hostOnly")
    val hostOnly: Boolean,
    @SerialName("httpOnly")
    val httpOnly: Boolean,
    @SerialName("id")
    val id: Int,
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

fun EditThisCookie.toCookie() = Cookie(
    name = name,
    value = value,
    encoding = CookieEncoding.DQUOTES,
    expires = GMTDate(expirationDate.toLong() * 1000),
    domain = domain,
    path = path,
    secure = secure,
    httpOnly = httpOnly
)