@file:Suppress("unused")

fun kotlinx(module: String, version: String = Versions.kotlin) =
    "org.jetbrains.kotlinx:kotlinx-$module:$version"

fun ktor(module: String, version: String= Versions.ktor) =
    "io.ktor:ktor-$module:$version"

fun mirai(module: String, version: String = "+") =
    "net.mamoe:mirai-$module:$version"

fun korlibs(module: String, version: String = "+") =
    "com.soywiz.korlibs.$module:$module:$version"

fun okhttp3(module: String, version: String = Versions.okhttp) =
    "com.squareup.okhttp3:$module:$version"