
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("net.mamoe.mirai-console")
}

group = "xyz.cssxsh.mirai.plugin"
version = "0.6.0-dev-1"

mirai {
    jvmTarget = JavaVersion.VERSION_11
    configureShadow {
        archiveBaseName.set(rootProject.name)
    }
}

repositories {
    mavenLocal()
    maven(url = "https://maven.aliyun.com/repository/central")
    maven(url = "https://maven.aliyun.com/repository/jcenter")
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
    maven(url = "https://bintray.proxy.ustclug.org/desmo/baidu-client/")
    mavenCentral()
    jcenter()
    gradlePluginPortal()
}

dependencies {
    implementation(ktor("client-serialization", Versions.ktor))
    implementation(ktor("client-encoding", Versions.ktor))
    implementation(jsoup(Versions.jsoup))
    implementation(mybatis("mybatis", Versions.mybatis))
    implementation(xerial("sqlite-jdbc", Versions.sqliteJdbc))
    implementation(project(":client"))
    implementation(project(":tools"))
    implementation(okhttp3("okhttp", Versions.okhttp))
    implementation(okhttp3("okhttp-dnsoverhttps", Versions.okhttp))
    implementation(cssxsh("baidu-oauth", Versions.baidu))
    implementation(cssxsh("baidu-netdisk", Versions.baidu))
    testImplementation(kotlin("test-junit5"))
    testImplementation(junit("api", Versions.junit))
    testRuntimeOnly(junit("engine", Versions.junit))
}

kotlin {
    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("kotlin.time.ExperimentalTime")
            languageSettings.useExperimentalAnnotation("net.mamoe.mirai.utils.MiraiInternalApi")
            languageSettings.useExperimentalAnnotation("net.mamoe.mirai.console.util.ConsoleExperimentalApi")
            languageSettings.useExperimentalAnnotation("io.ktor.util.KtorExperimentalAPI")
            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
            languageSettings.useExperimentalAnnotation("kotlinx.serialization.InternalSerializationApi")
            languageSettings.useExperimentalAnnotation("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}