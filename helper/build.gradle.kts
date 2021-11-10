
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.jpa")
    id("net.mamoe.mirai-console")
}

group = "xyz.cssxsh.mirai.plugin"
version = "1.6.4"

mirai {
    jvmTarget = JavaVersion.VERSION_11
    configureShadow {
        archiveBaseName.set(rootProject.name)
        exclude {
            it.path.startsWith("kotlin")
        }
        exclude {
            val features = listOf("auth", "compression", "json")
            it.path.startsWith("io/ktor") && features.none { f ->  it.path.startsWith("io/ktor/client/features/$f") }
        }
        exclude {
            it.path.startsWith("okhttp3/internal")
        }
        exclude {
            it.path.startsWith("okio")
        }
    }
}

repositories {
    mavenLocal()
    maven(url = "https://maven.aliyun.com/repository/public")
    mavenCentral()
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
    gradlePluginPortal()
}

dependencies {
    implementation(ktor("client-serialization", Versions.ktor))
    implementation(ktor("client-encoding", Versions.ktor))
    implementation(jsoup(Versions.jsoup))
    // implementation(mybatis("mybatis", Versions.mybatis))
    implementation(hibernate("hibernate-core", Versions.hibernate))
    implementation(hibernate("hibernate-c3p0", Versions.hibernate))
    implementation("com.github.gwenn:sqlite-dialect:0.1.2")
    implementation(xerial("sqlite-jdbc", Versions.sqlite))
    implementation(project(":client"))
    implementation(okhttp3("okhttp", Versions.okhttp))
    implementation(okhttp3("okhttp-dnsoverhttps", Versions.okhttp))
    implementation(cssxsh("baidu-oauth", Versions.baidu))
    implementation(cssxsh("baidu-netdisk", Versions.baidu))
    compileOnly("net.mamoe:mirai-core-jvm:2.8.0-RC")

    testImplementation(kotlin("test-junit5"))
    testImplementation(junit("api", Versions.junit))
    testImplementation("net.mamoe.yamlkt:yamlkt:0.10.0")
    testRuntimeOnly(junit("engine", Versions.junit))
    testRuntimeOnly("mysql:mysql-connector-java:8.0.26")
    testRuntimeOnly(fileTree(File(System.getenv("OPENCV_HOME")).resolve("build/java")))
}

kotlin {
    sourceSets {
        all {
//            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
//            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
//            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
//            languageSettings.useExperimentalAnnotation("net.mamoe.mirai.utils.MiraiInternalApi")
            languageSettings.optIn("net.mamoe.mirai.console.util.ConsoleExperimentalApi")
            languageSettings.optIn("net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors")
//            languageSettings.useExperimentalAnnotation("io.ktor.util.KtorExperimentalAPI")
//            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
//            languageSettings.useExperimentalAnnotation("kotlinx.serialization.InternalSerializationApi")
//            languageSettings.useExperimentalAnnotation("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

tasks {
    compileKotlin {
        kotlinOptions.freeCompilerArgs += "-Xunrestricted-builder-inference"
    }
    test {
        useJUnitPlatform()
    }
}