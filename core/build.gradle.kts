plugins {
    kotlin("jvm") version "1.8.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.10"
    `java-library`
}

group = "com.example"
version = "0.0.1"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    // coroutines
    api("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.6.4")
    api("org.jetbrains.kotlinx", "kotlinx-coroutines-reactive", "1.6.4")

    // serialization
    api("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.4.1")

    // logging
    api("io.github.microutils", "kotlin-logging-jvm", "3.0.5")
    api("ch.qos.logback", "logback-classic", "1.4.5")

    // di
    api("io.insert-koin", "koin-core", "3.3.3")

    // redis
    api("io.lettuce", "lettuce-core", "6.2.2.RELEASE")

    // mongo
    api("org.litote.kmongo", "kmongo", "4.8.0")
    api("org.litote.kmongo", "kmongo-coroutine", "4.8.0")
    api("org.litote.kmongo", "kmongo-coroutine-serialization", "4.8.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
