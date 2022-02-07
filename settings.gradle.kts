pluginManagement {
    plugins {
        kotlin("jvm") version "1.6.0"
        kotlin("plugin.serialization") version "1.6.0"
        kotlin("plugin.jpa") version "1.6.0"

        id("net.mamoe.mirai-console") version "2.10.0"
    }
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "pixiv-helper"

include("client")
include("helper")
