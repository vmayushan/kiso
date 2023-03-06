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

    // docker
    implementation("com.github.docker-java", "docker-java", "3.2.14")
    implementation("com.github.docker-java", "docker-java-transport-httpclient5", "3.2.14")

    // test
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

