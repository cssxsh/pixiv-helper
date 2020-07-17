import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin
    java
    id("com.github.johnrengelman.shadow") version Versions.shadow
}

group = "xyz.cssxsh.mirai.plugin"
version = "0.1.0"

repositories {
    maven(url = "https://maven.aliyun.com/repository/releases")
    maven(url = "https://mirrors.huaweicloud.com/repository/maven")
    // bintray
    maven(url = "https://bintray.proxy.ustclug.org/kotlin/kotlin-dev")
    maven(url = "https://bintray.proxy.ustclug.org/kotlin/kotlinx/")
    // central
    maven(url = "https://maven.aliyun.com/repository/central")
    mavenCentral()
    // jcenter
    maven(url = "https://maven.aliyun.com/repository/jcenter")
    jcenter()
}

dependencies {

    implementation(kotlin("stdlib", Versions.new))
    implementation(kotlin("serialization", Versions.new))
    implementation(kotlinx("serialization-runtime", Versions.new))
    implementation(kotlinx("serialization-runtime-common", Versions.new))
    implementation(kotlinx("serialization-protobuf", Versions.new))
    implementation(kotlinx("serialization-protobuf-common", Versions.new))
    implementation("net.mamoe:mirai-core:${Versions.new}")
    implementation("net.mamoe:mirai-core-qqandroid:${Versions.new}")
    implementation("net.mamoe:mirai-console:${Versions.new}")
    implementation("net.mamoe:mirai-console-pure:${Versions.new}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "11"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "11"
    }

    val runMiraiConsole by creating(JavaExec::class.java) {
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