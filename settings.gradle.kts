@file:Suppress("UnstableApiUsage")

pluginManagement {
    plugins {
        kotlin("jvm") version "1.6.0"
        kotlin("plugin.serialization") version "1.6.0"
        kotlin("plugin.jpa") version "1.6.0"

        id("net.mamoe.mirai-console") version "2.10.0-RC2"
    }
    repositories {
        mavenLocal()
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "pixiv-helper"

include("client")
include("helper")
