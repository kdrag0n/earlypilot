import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.4.32"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    kotlin("plugin.serialization") version "1.4.30"
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
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.apache.commons:commons-compress:1.20")
    implementation("com.google.guava:guava:30.1.1-jre")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")
    implementation("com.patreon:patreon:0.4.2") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }

    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
}

val nettyMainClass = "io.ktor.server.netty.EngineMain"
application {
    mainClass.set(nettyMainClass)
    // ShadowJar requires this deprecated property
    mainClassName = nettyMainClass
}