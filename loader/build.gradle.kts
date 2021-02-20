import org.gradle.kotlin.dsl.support.appendReproducibleNewLine

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
    implementation(mirai("core-api", Versions.mirai))
    implementation(mirai("console", Versions.mirai))
    implementation(mirai("core", Versions.mirai))
    implementation(mirai("console-terminal", Versions.mirai))
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

    val testConsoleDir = rootProject.projectDir.resolve( "test").apply { mkdir() }

    create("copyFile") {
        group = "mirai"

        dependsOn(project(":helper").getTasksByName("buildPlugin", false))
        dependsOn(testClasses)

        doFirst {
            testConsoleDir.resolve("plugins/").walk().filter {
                rootProject.name in it.name
            }.forEach {
                delete(it)
                println("Deleted ${it.absolutePath}")
            }
            copy {
                into(testConsoleDir.resolve( "plugins/"))
                from(project(":helper").buildDir.resolve("mirai/")) {
                    include {
                        rootProject.name in it.name
                    }.eachFile {
                        println("Copy ${file.absolutePath}")
                    }
                }
            }

            File(testConsoleDir, "start.bat").writeText(
                buildString {
                    appendReproducibleNewLine("@echo off")
                    appendReproducibleNewLine("cd ${testConsoleDir.absolutePath}")
                    appendReproducibleNewLine("java -classpath ${sourceSets.main.get().runtimeClasspath.asPath} ^")
                    appendReproducibleNewLine("-Dfile.encoding=UTF-8 ^")
                    appendReproducibleNewLine("-Xmx2000m ^")
                    appendReproducibleNewLine("mirai.RunMirai")
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