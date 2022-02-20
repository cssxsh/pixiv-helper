plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.jpa")
    id("net.mamoe.mirai-console")
}

group = "xyz.cssxsh.mirai.plugin"
version = "1.9.2"

mirai {
    configureShadow {
        archiveBaseName.set(rootProject.name)
        exclude("module-info.class")
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jsoup:jsoup:1.14.3")
    implementation(project(":client")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
        exclude(group = "io.ktor", module = "ktor-client-core")
        exclude(group = "io.ktor", module = "ktor-client-okhttp")
        exclude(group = "io.ktor", module = "ktor-network")
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    compileOnly("net.mamoe:mirai-core:2.10.0")
    compileOnly("net.mamoe:mirai-core-utils:2.10.0")
    compileOnly("io.github.gnuf0rce:netdisk-filesync-plugin:1.2.6")
    compileOnly("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.0.5")
    compileOnly("xyz.cssxsh.mirai:mirai-selenium-plugin:2.0.8")

    testImplementation(kotlin("test", "1.6.0"))
    testImplementation("net.mamoe.yamlkt:yamlkt:0.10.2")
    testImplementation("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.0.5")
    testImplementation("xyz.cssxsh.mirai:mirai-selenium-plugin:2.0.8")
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