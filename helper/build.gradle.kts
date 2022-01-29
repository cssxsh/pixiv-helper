plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.jpa")
    id("net.mamoe.mirai-console")
}

group = "xyz.cssxsh.mirai.plugin"
version = "1.9.0"

mirai {
    jvmTarget = JavaVersion.VERSION_11
    configureShadow {
        archiveBaseName.set(rootProject.name)
        exclude("module-info.class")
    }
}

repositories {
    mavenLocal()
    maven(url = "https://maven.aliyun.com/repository/central")
    mavenCentral()
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
    gradlePluginPortal()
}

dependencies {
    implementation("org.jsoup:jsoup:1.14.3")
    implementation(project(":client")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
        exclude(group = "io.ktor", module = "ktor-client-core")
        exclude(group = "io.ktor", module = "ktor-client-core-jvm")
        exclude(group = "io.ktor", module = "ktor-client-okhttp")
        exclude(group = "io.ktor", module = "ktor-network")
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    compileOnly(mirai("core", mirai.coreVersion))
    compileOnly(mirai("core-utils", mirai.coreVersion))
    compileOnly("io.github.gnuf0rce:netdisk-filesync-plugin:1.2.1")
    compileOnly("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.0.0")
    compileOnly("xyz.cssxsh.mirai:mirai-selenium-plugin:2.0.4")

    testImplementation(kotlin("test", kotlin.coreLibrariesVersion))
    testImplementation("net.mamoe.yamlkt:yamlkt:0.10.2")
    testImplementation("xyz.cssxsh.mirai:mirai-hibernate-plugin:1.0.4")
    testImplementation("xyz.cssxsh.mirai:mirai-selenium-plugin:2.0.4")
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