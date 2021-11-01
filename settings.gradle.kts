@file:Suppress("UnstableApiUsage")

pluginManagement {
    plugins {
        kotlin("jvm") version "1.5.30"
        kotlin("plugin.serialization") version "1.5.30"
        kotlin("plugin.jpa") version "1.5.30"

        id("net.mamoe.mirai-console") version "2.8.0-RC"
    }
    repositories {
        mavenLocal()
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        mavenCentral()
        jcenter()
        gradlePluginPortal()
    }
}

rootProject.name = "pixiv-helper"

include("client")
include("helper")
