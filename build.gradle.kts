plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"
    kotlin("plugin.jpa") version "1.7.10"

    id("net.mamoe.mirai-console") version "2.13.0-M1"
}

group = "xyz.cssxsh.mirai.plugin"
version = "2.0.0-M8"

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation("org.jsoup:jsoup:1.15.2")
    implementation("xyz.cssxsh.pixiv:pixiv-client:1.2.4") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
    }
    implementation("com.cronutils:cron-utils:9.2.0") {
        exclude(group = "org.slf4j")
        exclude(group = "org.glassfish")
        exclude(group = "org.javassist")
    }
    compileOnly("javax.validation:validation-api:2.0.1.Final")
    compileOnly("net.mamoe:mirai-core:2.13.0-M1")
    compileOnly("net.mamoe:mirai-core-utils:2.13.0-M1")
    // dependsOn
    compileOnly("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.4.3")
    compileOnly("xyz.cssxsh.mirai:mirai-selenium-plugin:2.2.2")
    compileOnly("xyz.cssxsh.mirai:mirai-skia-plugin:1.1.7")

    testImplementation(kotlin("test"))
    testImplementation("net.mamoe:mirai-slf4j-bridge:1.2.0")
    testImplementation("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.4.3")
    testImplementation("xyz.cssxsh.mirai:mirai-selenium-plugin:2.2.2")
    testImplementation("xyz.cssxsh.mirai:mirai-skia-plugin:1.1.7")
}

mirai {
    jvmTarget = JavaVersion.VERSION_11
}

noArg {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
}

kotlin {
    explicitApi()
}

tasks {
    test {
        useJUnitPlatform()
    }
}