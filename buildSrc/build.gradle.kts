plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    jcenter()
    maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
    mavenCentral()
}

kotlin {
    sourceSets.all {
        languageSettings.useExperimentalAnnotation("kotlin.Experimental")
        languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
    }
}

dependencies {
}