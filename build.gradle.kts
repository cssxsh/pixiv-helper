plugins {
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
    kotlin("plugin.jpa") version "1.6.21"

    id("net.mamoe.mirai-console") version "2.11.0"
}

group = "xyz.cssxsh.mirai.plugin"
version = "1.10.0-M1"

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation("org.jsoup:jsoup:1.14.3")
    implementation("xyz.cssxsh.pixiv:pixiv-client:1.0.2") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
        exclude(group = "io.ktor", module = "ktor-client-core")
        exclude(group = "io.ktor", module = "ktor-client-okhttp")
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    compileOnly("net.mamoe:mirai-core:2.11.0")
    compileOnly("net.mamoe:mirai-core-utils:2.11.0")
    // dependsOn
    compileOnly("io.github.gnuf0rce:netdisk-filesync-plugin:1.3.0")
    compileOnly("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.2.0")
    compileOnly("xyz.cssxsh.mirai:mirai-selenium-plugin:2.1.0")
    compileOnly("xyz.cssxsh.mirai:mirai-skia-plugin:1.0.4")

    testImplementation(kotlin("test", "1.6.21"))
    testImplementation("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.2.0")
    testImplementation("xyz.cssxsh.mirai:mirai-selenium-plugin:2.1.0")
    testImplementation("xyz.cssxsh.mirai:mirai-skia-plugin:1.0.4")
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