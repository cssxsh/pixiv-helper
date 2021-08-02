@file:Suppress("unused")

import org.gradle.api.artifacts.dsl.DependencyHandler

fun DependencyHandler.kotlinx(module: String, version: String) =
    "org.jetbrains.kotlinx:kotlinx-$module:$version"

fun DependencyHandler.ktor(module: String, version: String) =
    "io.ktor:ktor-$module:$version"

fun DependencyHandler.mirai(module: String, version: String) =
    "net.mamoe:mirai-$module:$version"

fun DependencyHandler.okhttp3(module: String, version: String) =
    "com.squareup.okhttp3:$module:$version"

fun DependencyHandler.jsoup(version: String) =
    "org.jsoup:jsoup:$version"

fun DependencyHandler.mybatis(module: String, version: String) =
    "org.mybatis:${module}:${version}"

fun DependencyHandler.mysql(module: String, version: String) =
    "mysql:${module}:${version}"

fun DependencyHandler.hibernate(module: String, version: String) =
    "org.hibernate:${module}:${version}"

fun DependencyHandler.xerial(module: String, version: String) =
    "org.xerial:${module}:${version}"

fun DependencyHandler.junit(module: String, version: String) =
    "org.junit.jupiter:junit-jupiter-${module}:${version}"

fun DependencyHandler.cssxsh(module: String, version: String) =
    "xyz.cssxsh.${module.substringBeforeLast('-')}:${module}:${version}"