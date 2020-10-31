pluginManagement {
    repositories {
        mavenLocal()
        maven(url = "https://maven.aliyun.com/repository/releases")
        maven(url = "https://mirrors.huaweicloud.com/repository/maven")
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
