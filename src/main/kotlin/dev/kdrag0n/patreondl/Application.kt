package dev.kdrag0n.patreondl

import io.ktor.locations.*
import io.ktor.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@KtorExperimentalLocationsAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(production: Boolean = false) {
    // Install Ktor server features
    featuresModule(production)

    // Error handling
    errorsModule()

    // Patreon OAuth and session authentication + validation
    authModule(production)

    // Exclusive content
    exclusiveModule()

    // Simple Public/static routes
    staticModule()
}