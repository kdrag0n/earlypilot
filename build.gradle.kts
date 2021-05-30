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
    kotlin("jvm") version "1.5.10"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    kotlin("plugin.serialization") version "1.5.10"
}

group = "dev.kdrag0n"
version = "0.0.1"

repositories {
    mavenCentral()
    mavenLocal()
    jcenter()
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
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-mustache:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")

    // Internal
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.apache.commons:commons-compress:1.20")
    implementation("com.google.guava:guava:30.1.1-jre")
    implementation("io.sentry:sentry:4.3.0")

    // Architecture
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-core-ext:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.sksamuel.hoplite:hoplite-hocon:$hopliteVersion")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:0.15.2")

    // API
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
    implementation("com.patreon:patreon:0.4.2") {
        // Exclude duplicate SLF4J implementation
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
    implementation("com.sendgrid:sendgrid-java:4.7.2") {
        // Newer version of Apache HTTP client breaks Patreon API library
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("dev.inmo:tgbotapi:$tgbotVersion")
    implementation("dev.inmo:tgbotapi.extensions.api:$tgbotVersion")
    implementation("com.stripe:stripe-java:20.52.0")
    implementation("com.maxmind.geoip2:geoip2:2.15.0") {
        // Newer version of Apache HTTP client breaks Patreon API library
        exclude(group = "org.apache.httpcomponents")
    }

    // Database
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("org.postgresql:postgresql:42.2.19")

    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
}

val nettyMainClass = "io.ktor.server.netty.EngineMain"
application {
    mainClass.set(nettyMainClass)
    // ShadowJar requires this deprecated property
    mainClassName = nettyMainClass
}
