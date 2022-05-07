plugins {
    kotlin("jvm") version "1.6.0"
    kotlin("plugin.serialization") version "1.6.0"
    kotlin("plugin.jpa") version "1.6.0"

    id("net.mamoe.mirai-console") version "2.10.2"
}

group = "xyz.cssxsh.mirai.plugin"
version = "1.9.6"

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation("org.jsoup:jsoup:1.14.3")
    implementation("xyz.cssxsh.pixiv:pixiv-client:1.0.0") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
        exclude(group = "io.ktor", module = "ktor-client-core")
        exclude(group = "io.ktor", module = "ktor-client-okhttp")
        exclude(group = "io.ktor", module = "ktor-network")
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    compileOnly("net.mamoe:mirai-core:2.10.2")
    compileOnly("net.mamoe:mirai-core-utils:2.10.2")
    compileOnly("io.github.gnuf0rce:netdisk-filesync-plugin:1.2.6")
    compileOnly("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.1.0")
    compileOnly("xyz.cssxsh.mirai:mirai-selenium-plugin:2.0.9")
    compileOnly("xyz.cssxsh.mirai:mirai-skia-plugin:1.0.4-M2")

    testImplementation(kotlin("test", "1.6.0"))
    testImplementation("net.mamoe.yamlkt:yamlkt:0.10.2")
    testImplementation("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.1.0")
    testImplementation("xyz.cssxsh.mirai:mirai-selenium-plugin:2.0.9")
    testImplementation("xyz.cssxsh.mirai:mirai-skia-plugin:1.0.4-M2")
}

kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
    }
}

tasks {
    compileKotlin {
        kotlinOptions.freeCompilerArgs += "-Xunrestricted-builder-inference"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
    test {
        useJUnitPlatform()
    }
}