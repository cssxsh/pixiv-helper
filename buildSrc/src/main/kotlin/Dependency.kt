@file:Suppress("unused")

import org.gradle.api.artifacts.dsl.DependencyHandler

fun DependencyHandler.kotlinx(module: String, version: String = Versions.kotlin) =
    "org.jetbrains.kotlinx:kotlinx-$module:$version"

fun DependencyHandler.ktor(module: String, version: String= Versions.ktor) =
    "io.ktor:ktor-$module:$version"

fun DependencyHandler.mirai(module: String, version: String = "+") =
    "net.mamoe:mirai-$module:$version"

fun DependencyHandler.okhttp3(module: String, version: String = Versions.okhttp) =
    "com.squareup.okhttp3:$module:$version"

fun DependencyHandler.jsoup(version: String = Versions.jsoup) =
    "org.jsoup:jsoup:$version"

fun DependencyHandler.poi(module: String, version: String = Versions.poi) =
    "org.apache.poi:${module}:$version"

fun DependencyHandler.mybatis(module: String, version: String = Versions.mybatis) =
    "org.mybatis:${module}:${version}"

fun DependencyHandler.xerial(module: String, version: String) =
    "org.xerial:${module}:${version}"