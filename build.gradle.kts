import java.io.*

plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin
    id("com.github.johnrengelman.shadow") version Versions.shadow
}

group = "xyz.cssxsh.mirai.plugin"
version = "0.5.0-dev"

repositories {
    mavenLocal()
    gradlePluginPortal()
    maven(url = "https://maven.aliyun.com/repository/releases")
    maven(url = "https://mirrors.huaweicloud.com/repository/maven")
    // bintray dl.bintray.com -> bintray.proxy.ustclug.org
    maven(url = "https://bintray.proxy.ustclug.org/him188moe/mirai/")
    maven(url = "https://bintray.proxy.ustclug.org/kotlin/kotlin-dev")
    maven(url = "https://bintray.proxy.ustclug.org/kotlin/kotlinx/")
    maven(url = "https://bintray.proxy.ustclug.org/korlibs/korlibs/")
    // central
    maven(url = "https://maven.aliyun.com/repository/central")
    mavenCentral()
    // jcenter
    maven(url = "https://maven.aliyun.com/repository/jcenter")
    jcenter()
}

dependencies {
    //
    implementation(kotlin("stdlib", Versions.kotlin))
    implementation("net.mamoe:mirai-core:${Versions.core}")
    implementation("net.mamoe:mirai-console:${Versions.console}")
    implementation("com.soywiz.korlibs.klock:klock:${Versions.klock}")
    implementation(group = "xzy.cssxsh.pixiv", name = "pixiv-client-jvm", version = "0.6.0-dev-3")
    // test
    testImplementation("net.mamoe:mirai-console-pure:${Versions.console}")
    testImplementation("org.junit.jupiter:junit-jupiter:${Versions.junit}")
    // testImplementation(kotlinx("coroutines-test", Versions.coroutines))
}

kotlin {
    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("net.mamoe.mirai.console.util.ConsoleExperimentalAPI")
            languageSettings.useExperimentalAnnotation("net.mamoe.mirai.console.data.ExperimentalPluginConfig")
            languageSettings.useExperimentalAnnotation("com.soywiz.klock.annotations.KlockExperimental")
            languageSettings.useExperimentalAnnotation("io.ktor.util.KtorExperimentalAPI")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks {

    compileKotlin {
        kotlinOptions.freeCompilerArgs += "-Xjvm-default=enable"
        kotlinOptions.jvmTarget = "11"
    }

    compileTestKotlin {
        kotlinOptions.freeCompilerArgs += "-Xjvm-default=enable"
        kotlinOptions.jvmTarget = "11"
    }

    val testConsoleDir = "test"

    create("copyFile") {
        group = "mirai"

        dependsOn(shadowJar)
        dependsOn(testClasses)

        doFirst {

            val workingDir = File(testConsoleDir).apply { mkdir() }

            File(workingDir, "plugins").apply { mkdir() }.walk().forEach {
                if ( project.name in it.name) {
                    check(it.delete())
                    println("deleting old files: ${it.name}")
                }
            }

            File("build/libs/").walk().filter {
                "-all" in it.name
            }.maxBy {
                it.lastModified()
            }?.run {
                println("Coping $name")
                File("$testConsoleDir/plugins/${name}").apply {
                    check(createNewFile())
                }.let {
                    inputStream().transferTo(it.outputStream())
                }
                println("Copied $name")
            }
        }
    }


    create("runMiraiConsole", JavaExec::class.java) {
        group = "mirai"

        dependsOn(named("copyFile"))

        main = "mirai.RunMirai"

        // debug = true
        defaultCharacterEncoding = "UTF-8"

        doFirst {
            workingDir = File(testConsoleDir)
            classpath = sourceSets["test"].runtimeClasspath
            standardInput = System.`in`
            args(Versions.core, Versions.console)
        }
    }
}