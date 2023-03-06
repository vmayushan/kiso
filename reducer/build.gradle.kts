plugins {
    kotlin("jvm") version "1.8.10"
    kotlin("plugin.serialization") version "1.8.10"
    application
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // core reference
    api(project(mapOf("path" to ":core")))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

