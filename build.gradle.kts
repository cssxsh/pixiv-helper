plugins {
    kotlin("jvm") version "1.8.10"
    kotlin("plugin.serialization") version "1.8.10"
    kotlin("plugin.jpa") version "1.8.10"

    id("net.mamoe.mirai-console") version "2.15.0-RC"
}

group = "xyz.cssxsh"
version = "2.0.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.7.1")
    compileOnly("xyz.cssxsh.mirai:mirai-selenium-plugin:2.3.0")
    compileOnly("xyz.cssxsh.mirai:mirai-skia-plugin:1.3.1")
    compileOnly("xyz.cssxsh:arknights-helper:2.1.7")
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("xyz.cssxsh.pixiv:pixiv-client:1.3.1")
    implementation("com.cronutils:cron-utils:9.2.1")
    implementation("org.jcodec:jcodec:0.2.5")
    implementation("org.jcodec:jcodec-javase:0.2.5")
    testImplementation(kotlin("test"))
    testImplementation("xyz.cssxsh.mirai:mirai-hibernate-plugin:2.7.1")
    testImplementation("xyz.cssxsh.mirai:mirai-selenium-plugin:2.3.0")
    testImplementation("xyz.cssxsh.mirai:mirai-skia-plugin:1.3.1")
    //
    implementation(platform("net.mamoe:mirai-bom:2.15.0-RC"))
    compileOnly("net.mamoe:mirai-core")
    compileOnly("net.mamoe:mirai-core-utils")
    compileOnly("net.mamoe:mirai-console-compiler-common")
    testImplementation("net.mamoe:mirai-logging-slf4j")
    //
    implementation(platform("org.slf4j:slf4j-parent:2.0.7"))
    testImplementation("org.slf4j:slf4j-simple")
}

mirai {
    jvmTarget = JavaVersion.VERSION_11
}

kotlin {
    explicitApi()
}

tasks {
    test {
        useJUnitPlatform()
    }
}