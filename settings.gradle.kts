pluginManagement {
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.github.johnrengelman.shadow" -> useModule("com.github.jengelman.gradle.plugins:shadow:${requested.version}")
                "org.jetbrains.kotlin.jvm" -> useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }

    repositories {
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
        maven(url = "https://mirrors.huaweicloud.com/repository/maven")
        mavenCentral()
        jcenter()
    }
}

rootProject.name = "PixivHelper"
