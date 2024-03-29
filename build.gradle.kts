import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion: String by project
val kotlinVersion: String by project
val koinVersion: String by project

val hopliteVersion: String by project
val logbackVersion: String by project
val exposedVersion: String by project

val tgbotVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.7.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("plugin.serialization") version "1.7.0"
}

group = "dev.kdrag0n"
version = "0.0.1"

repositories {
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://kotlin.bintray.com/ktor") }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "13"
    kotlinOptions.freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    // Ktor
    implementation("io.ktor:ktor-server:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-locations:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-mustache:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Internal
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("io.sentry:sentry:6.0.0")

    // Architecture
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.sksamuel.hoplite:hoplite-hocon:$hopliteVersion")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.3.0")

    // API
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("com.patreon:patreon:0.4.2") {
        // Exclude duplicate SLF4J implementation
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
    implementation("dev.inmo:tgbotapi:$tgbotVersion")
    implementation("com.stripe:stripe-java:20.127.0")
    implementation("com.maxmind.geoip2:geoip2:3.0.1")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.postgresql:postgresql:42.3.6")

    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
}

val nettyMainClass = "io.ktor.server.netty.EngineMain"
application {
    mainClass.set(nettyMainClass)
    // ShadowJar requires this deprecated property
    mainClassName = nettyMainClass
}
