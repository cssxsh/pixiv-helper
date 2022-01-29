@file:Suppress("unused")

import org.gradle.api.artifacts.dsl.DependencyHandler

fun DependencyHandler.kotlinx(module: String, version: String) = "org.jetbrains.kotlinx:kotlinx-$module:$version"

fun DependencyHandler.ktor(module: String, version: String) = "io.ktor:ktor-$module:$version"

fun DependencyHandler.mirai(module: String, version: String) = "net.mamoe:mirai-$module:$version"

fun DependencyHandler.okhttp3(module: String, version: String) = "com.squareup.okhttp3:$module:$version"

fun DependencyHandler.square(module: String, version: String) = "com.squareup:$module:$version"