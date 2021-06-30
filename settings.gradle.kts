@file:Suppress("UnstableApiUsage")

pluginManagement {
    plugins {
        kotlin("jvm") version "1.5.10"
        kotlin("plugin.serialization") version "1.5.10"

        id("net.mamoe.mirai-console") version "2.6.7"
    }
    repositories {
        mavenLocal()
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://maven.aliyun.com/repository/jcenter")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        mavenCentral()
        jcenter()
        gradlePluginPortal()
    }
}

rootProject.name = "pixiv-helper"

include("client")
include("helper")
include("tools")
