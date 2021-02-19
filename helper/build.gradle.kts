
plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin
    id("net.mamoe.mirai-console") version Versions.mirai
}

group = "xyz.cssxsh.mirai.plugin"
version = "0.6.0-dev-1"

mirai {
    jvmTarget = JavaVersion.VERSION_11
}

repositories {
    mavenLocal()
    maven(url = "https://maven.aliyun.com/repository/releases")
    maven(url = "https://mirrors.huaweicloud.com/repository/maven")
    // bintray dl.bintray.com -> bintray.proxy.ustclug.org
    maven(url = "https://bintray.proxy.ustclug.org/him188moe/mirai/")
    maven(url = "https://bintray.proxy.ustclug.org/kotlin/kotlin-dev")
    maven(url = "https://bintray.proxy.ustclug.org/kotlin/kotlinx/")
    // central
    maven(url = "https://maven.aliyun.com/repository/central")
    mavenCentral()
    // jcenter
    maven(url = "https://maven.aliyun.com/repository/jcenter")
    jcenter()
    gradlePluginPortal()
}

dependencies {
    implementation(ktor("client-serialization", Versions.ktor))
    implementation(ktor("client-encoding", Versions.ktor))
    implementation(jsoup(Versions.jsoup))
    implementation(poi("poi-ooxml", Versions.poi))
    implementation(mybatis("mybatis", Versions.mybatis))
    implementation(xerial("sqlite-jdbc", Versions.sqliteJdbc))
    implementation(project(":client"))
}

kotlin {
    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("kotlin.time.ExperimentalTime")
            languageSettings.useExperimentalAnnotation("net.mamoe.mirai.utils.MiraiInternalApi")
            languageSettings.useExperimentalAnnotation("io.ktor.util.KtorExperimentalAPI")
            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}