import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin
    id("com.github.johnrengelman.shadow") version Versions.shadow
}

group = "xyz.cssxsh.mirai.plugin"
version = "0.1.0"

repositories {
    mavenLocal()
    gradlePluginPortal()
    maven(url = "https://maven.aliyun.com/repository/releases")
    maven(url = "https://mirrors.huaweicloud.com/repository/maven")
    // bintray
    maven(url = "https://bintray.proxy.ustclug.org/kotlin/kotlin-eap")
    maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
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
    implementation(group = "xzy.cssxsh.pixiv", name = "pixiv-client-jvm", version = "0.1.0-dev-1")
    // test
    testImplementation("net.mamoe:mirai-console-pure:${Versions.console}")
    testImplementation("org.junit.jupiter:junit-jupiter:${Versions.junit}")
    // testImplementation(kotlinx("coroutines-test", Versions.coroutines))
}

kotlin {
    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("net.mamoe.mirai.console.util.ConsoleExperimentalAPI")
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

    create("runMiraiConsole", JavaExec::class.java) {
        group = "mirai"
        main = "mirai.RunMirai"

        dependsOn(shadowJar)
        dependsOn(testClasses)

        val testConsoleDir = "test"

        debugOptions {
            port.set(5005)
        }

        doFirst {
            fun removeOldVersions() {
                File("$testConsoleDir/plugins/").walk()
                    .filter { it.name.matches(Regex("""${project.name}-.*-all.jar""")) }
                    .forEach {
                        it.delete()
                        println("deleting old files: ${it.name}")
                    }
            }

            fun copyBuildOutput() {
                File("build/libs/").walk()
                    .filter { it.name.contains("-all") }
                    .maxBy { it.lastModified() }
                    ?.let {
                        println("Coping ${it.name}")
                        it.inputStream()
                            .transferTo1(File("$testConsoleDir/plugins/${it.name}").apply { createNewFile() }
                                .outputStream())
                        println("Copied ${it.name}")
                    }
            }

            workingDir = File(testConsoleDir)
            workingDir.mkdir()
            File(workingDir, "plugins").mkdir()
            removeOldVersions()
            copyBuildOutput()

            classpath = sourceSets["test"].runtimeClasspath
            standardInput = System.`in`
            args(Versions.core, Versions.console)
        }
    }
}


@Throws(IOException::class)
fun InputStream.transferTo1(out: OutputStream): Long {
    Objects.requireNonNull(out, "out")
    var transferred: Long = 0
    val buffer = ByteArray(8192)
    var read: Int
    while (this.read(buffer, 0, 8192).also { read = it } >= 0) {
        out.write(buffer, 0, read)
        transferred += read.toLong()
    }
    return transferred
}