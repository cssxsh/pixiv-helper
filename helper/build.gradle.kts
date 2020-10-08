plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin
    kotlin("kapt") version Versions.kotlin
    id("com.github.johnrengelman.shadow") version Versions.shadow
}

group = "xyz.cssxsh.mirai.plugin"
version = "0.5.0-dev-2"

repositories {
    mavenLocal()
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
    gradlePluginPortal()
}


dependencies {
    kapt(group = "com.google.auto.service", name = "auto-service", version = Versions.autoService)
    compileOnly(group = "com.google.auto.service", name = "auto-service-annotations", version = Versions.autoService)
    implementation(kotlin("stdlib", Versions.kotlin))
    implementation(mirai("core", Versions.core))
    implementation(mirai("console", Versions.console))
    implementation(korlibs("klock", Versions.klock))
    implementation(jsoup(Versions.jsoup))
    // implementation(group = "xzy.cssxsh.pixiv", name = "pixiv-client-jvm", version = "0.7.0-dev-7")
    implementation(project(":client"))
    // test
    testImplementation(mirai("core-qqandroid", Versions.core))
    testImplementation(mirai("console-terminal", Versions.console))
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = Versions.junit)
    // testImplementation(kotlinx("coroutines-test", Versions.coroutines))
}


kotlin {
    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("com.soywiz.klock.annotations.KlockExperimental")
            languageSettings.useExperimentalAnnotation("io.ktor.util.KtorExperimentalAPI")
        }
        test {
            languageSettings.useExperimentalAnnotation("net.mamoe.mirai.console.ConsoleFrontEndImplementation")
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
       }
    }

    compileKotlin {
        kotlinOptions.freeCompilerArgs += "-Xjvm-default=all"
        kotlinOptions.jvmTarget = "11"
    }

    compileTestKotlin {
        kotlinOptions.freeCompilerArgs += "-Xjvm-default=all"
        kotlinOptions.jvmTarget = "11"
    }

    val testConsoleDir = File(parent?.projectDir ?: projectDir, "test").apply { mkdir() }

    create("copyFile") {
        group = "mirai"

        dependsOn(shadowJar)
        dependsOn(testClasses)


        doFirst {
            delete {
                File(testConsoleDir, "plugins/").walk().filter {
                    project.name in it.name
                }.forEach {
                    delete(it)
                    println("Deleted ${it.toURI()}")
                }
            }
            copy {
                into(File(testConsoleDir, "plugins/").walk().maxBy {
                    it.lastModified()
                }.let {
                    requireNotNull(it) { "没有要复制的文件" }
                })
                from(File(project.buildDir, "libs/")) {
                    include {
                        "${project.name}-${version}-all" in it.name
                    }.eachFile {
                        println("Copy ${file.toURI()}")
                    }
                }
            }
        }
    }


    create("runMiraiConsole", JavaExec::class.java) javaExec@ {
        group = "mirai"

        dependsOn(named("copyFile"))

        main = "mirai.RunMirai"

        // debug = true

        defaultCharacterEncoding = "UTF-8"

        workingDir = testConsoleDir

        standardInput = System.`in`

        // jvmArgs("-Djavax.net.debug=all")

        doFirst {
            classpath = sourceSets["test"].runtimeClasspath
            println("WorkingDir: ${workingDir.toURI()}, Args: $args")
        }
    }
}