pluginManagement {
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.github.johnrengelman.shadow" -> useModule("com.github.jengelman.gradle.plugins:shadow:${requested.version}")
                "org.jetbrains.kotlin.jvm" -> useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }

    repositories {
        maven(url = "https://maven.aliyun.com/repository/releases")
        maven(url = "https://mirrors.huaweicloud.com/repository/maven")
        mavenLocal()
        gradlePluginPortal()
        // bintray
        maven(url = "https://bintray.proxy.ustclug.org/kotlin/kotlin-eap")
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
        // central
        maven(url = "https://maven.aliyun.com/repository/central")
        mavenCentral()
        // jcenter
        maven(url = "https://maven.aliyun.com/repository/jcenter")
        jcenter()
    }
}

rootProject.name = "pixiv-helper"

include("client")
include("helper")
