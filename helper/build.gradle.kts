plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.jpa")
    id("net.mamoe.mirai-console")
}

group = "xyz.cssxsh.mirai.plugin"
version = "1.8.0"

mirai {
    jvmTarget = JavaVersion.VERSION_11
    configureShadow {
        archiveBaseName.set(rootProject.name)
        exclude("module-info.class")
        exclude {
            it.path.startsWith("kotlin")
        }
        exclude {
            val features = listOf("auth", "compression", "json")
            it.path.startsWith("io/ktor") && features.none { f -> it.path.startsWith("io/ktor/client/features/$f") }
        }
        exclude {
            it.path.startsWith("okhttp3") && it.path.startsWith("okhttp3/dnsoverhttps").not()
        }
        exclude {
            it.path.startsWith("okio")
        }
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
    implementation(ktor("client-serialization", Versions.ktor)) {
        exclude("org.jetbrains.kotlinx")
    }
    implementation(ktor("client-encoding", Versions.ktor))
    implementation(jsoup(Versions.jsoup))
    implementation(hibernate("hibernate-core", Versions.hibernate))
    implementation(hibernate("hibernate-c3p0", Versions.hibernate))
    implementation("com.github.gwenn:sqlite-dialect:0.1.2")
    implementation(xerial("sqlite-jdbc", Versions.sqlite))
    implementation(project(":client"))
    implementation(okhttp3("okhttp", Versions.okhttp))
    implementation(okhttp3("okhttp-dnsoverhttps", Versions.okhttp))
    compileOnly("io.github.gnuf0rce:netdisk-filesync-plugin:1.2.1")
    compileOnly("net.mamoe:mirai-core-jvm:2.9.0-RC2")
    compileOnly("mysql:mysql-connector-java:8.0.26")

    testImplementation(kotlin("test", "1.6.0"))
    testImplementation("net.mamoe.yamlkt:yamlkt:0.10.2")
    testImplementation("mysql:mysql-connector-java:8.0.26")
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