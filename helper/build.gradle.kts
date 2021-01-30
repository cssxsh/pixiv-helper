
plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin
    kotlin("kapt") version Versions.kotlin
    id("com.github.johnrengelman.shadow") version Versions.shadow
//    id("net.mamoe.mirai-console") version Versions.console
}

group = "xyz.cssxsh.mirai.plugin"
version = "0.6.0-dev-1"

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
    kapt(group = "com.google.auto.service", name = "auto-service", version = Versions.autoService)
    compileOnly(group = "com.google.auto.service", name = "auto-service-annotations", version = Versions.autoService)
    compileOnly(mirai("core-api", Versions.core))
    compileOnly(mirai("console", Versions.console))
    implementation(ktor("client-core", Versions.ktor))
    implementation(ktor("client-serialization", Versions.ktor))
    implementation(ktor("client-encoding", Versions.ktor))
    implementation(ktor("client-okhttp", Versions.ktor))
    implementation(jsoup(Versions.jsoup))
    implementation(poi("poi-ooxml", Versions.poi))
    implementation(mybatis("mybatis", Versions.mybatis))
    implementation(xerial("sqlite-jdbc", Versions.sqliteJdbc))
    implementation(project(":client"))
    // test
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = Versions.junit)
    // testImplementation(kotlinx("coroutines-test", Versions.coroutines))
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

    shadowJar {
        dependencies {
            exclude { "org.jetbrains" in it.moduleGroup }
            exclude { "net.mamoe" in it.moduleGroup }
            exclude { "org.slf4j" in it.moduleGroup }
        }
        archiveBaseName.set(rootProject.name)
    }

    compileKotlin {
        kotlinOptions.freeCompilerArgs += "-Xjvm-default=all"
        kotlinOptions.jvmTarget = "11"
    }

    compileTestKotlin {
        kotlinOptions.freeCompilerArgs += "-Xjvm-default=all"
        kotlinOptions.jvmTarget = "11"
    }
}