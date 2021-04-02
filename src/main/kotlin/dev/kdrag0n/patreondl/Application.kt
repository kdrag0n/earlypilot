package dev.kdrag0n.patreondl

import io.ktor.locations.*
import io.ktor.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@KtorExperimentalLocationsAPI
@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    // Install Ktor server features
    featuresModule()

    // Error handling
    errorsModule()

    // Patreon OAuth and session authentication + validation
    authModule()

    // Exclusive content
    exclusiveModule()

    // Simple Public/static routes
    staticModule()
}