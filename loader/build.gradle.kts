plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin
}

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
    implementation(mirai("core-api", Versions.core))
    implementation(mirai("console", Versions.console))
    implementation(mirai("core", Versions.core))
    implementation(mirai("console-terminal", Versions.console))
    compileOnly(ktor("client-core", Versions.ktor))
    compileOnly(ktor("client-serialization", Versions.ktor))
    compileOnly(ktor("client-encoding", Versions.ktor))
    compileOnly(ktor("client-okhttp", Versions.ktor))
    // test
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = Versions.junit)
    // testImplementation(kotlinx("coroutines-test", Versions.coroutines))
}

kotlin {
    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("io.ktor.util.KtorExperimentalAPI")
            languageSettings.useExperimentalAnnotation("net.mamoe.mirai.console.ConsoleFrontEndImplementation")
        }
    }
}

tasks {

    test {
        useJUnitPlatform()
    }

    compileKotlin {
        kotlinOptions.freeCompilerArgs += "-Xjvm-default=all"
        kotlinOptions.jvmTarget = "11"
    }

    compileTestKotlin {
        kotlinOptions.freeCompilerArgs += "-Xjvm-default=all"
        kotlinOptions.jvmTarget = "11"
    }

    val testConsoleDir = File(rootProject.projectDir, "test").apply { mkdir() }

    create("copyFile") {
        group = "mirai"

        dependsOn(project(":helper").getTasksByName("shadowJar", false))
        dependsOn(testClasses)

        doFirst {
            File(testConsoleDir, "plugins/").walk().filter {
                rootProject.name in it.name
            }.forEach {
                delete(it)
                println("Deleted ${it.absolutePath}")
            }
            copy {
                into(File(testConsoleDir, "plugins/"))
                from(File(project(":helper").buildDir, "libs/")) {
                    include {
                        rootProject.name in it.name
                    }.eachFile {
                        println("Copy ${file.absolutePath}")
                    }
                }
            }

            File(testConsoleDir, "start.bat").writeText(
                buildString {
                    appendln("@echo off")
                    appendln("cd ${testConsoleDir.absolutePath}")
                    appendln("java -classpath ${sourceSets.main.get().runtimeClasspath.asPath} ^")
                    appendln("-Dfile.encoding=UTF-8 ^")
                    appendln("-Xmx2000m ^")
                    appendln("mirai.RunMirai")
                }
            )
        }
    }

    create("runMiraiConsole", JavaExec::class.java) {
        group = "mirai"

        dependsOn(named("copyFile"))

        main = "mirai.RunMirai"

        // debug = true

        defaultCharacterEncoding = "UTF-8"

        workingDir = testConsoleDir

        standardInput = System.`in`

        // jvmArgs("-Djavax.net.debug=all")

        doFirst {
            classpath = sourceSets.main.get().runtimeClasspath
            println("WorkingDir: ${workingDir.absolutePath}, Args: $args")
        }
    }
}