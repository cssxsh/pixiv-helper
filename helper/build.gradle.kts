import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin
    kotlin("kapt") version Versions.kotlin
    id("com.github.johnrengelman.shadow") version Versions.shadow
}

group = "xyz.cssxsh.mirai.plugin"
version = "0.5.0-dev-1"

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
    implementation(group = "com.soywiz.korlibs.klock", name = "klock", version = Versions.klock)
    // implementation(group = "xzy.cssxsh.pixiv", name = "pixiv-client-jvm", version = "0.7.0-dev-7")
    // test
    testImplementation(mirai("console-pure", Versions.console))
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
    }
}

tasks {

    withType<Test> {
        useJUnitPlatform()
    }

    withType<ShadowJar> {
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

    val testConsoleDir = "test"

    create("copyFile") {
        group = "mirai"

        dependsOn(shadowJar)
        dependsOn(testClasses)

        doFirst {
            val workingDir = File(testConsoleDir).apply { mkdir() }

            File(workingDir, "plugins").apply { mkdir() }.walk().forEach {
                if (project.name in it.name) {
                    check(it.delete())
                    println("deleting old files: ${it.name}")
                }
            }


            println("Coping $name")
            File("build/libs/").walk().filter {
                "-all" in it.name
            }.maxBy {
                it.lastModified()
            }?.let {
                copy {
                    from(it)
                    into("$testConsoleDir/plugins/")
                }
                /*
                File("$testConsoleDir/plugins/${name}").apply {
                    check(createNewFile())
                }.let {
                    inputStream().transferTo(it.outputStream())
                }*/
            }
            println("Copied $name")
        }
    }


    create("runMiraiConsole", JavaExec::class.java) {
        group = "mirai"

        dependsOn(named("copyFile"))

        main = "mirai.RunMirai"

        // debug = true
        defaultCharacterEncoding = "UTF-8"

        doFirst {
            workingDir = File(rootDir, testConsoleDir)
            classpath = sourceSets["test"].runtimeClasspath
            standardInput = System.`in`
            args(Versions.core, Versions.console)
        }
    }
}