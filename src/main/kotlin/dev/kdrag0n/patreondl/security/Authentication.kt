package dev.kdrag0n.patreondl.security

import dev.kdrag0n.patreondl.external.patreon.installPatreonAuthProvider
import io.ktor.server.application.*
import io.ktor.server.locations.*

@KtorExperimentalLocationsAPI
fun Application.authModule() {
    // Session for storing OAuth access tokens
    installPatronSessions()

    // Patreon authentication provider
    installPatreonAuthProvider()

    // Grant authentication provider
    installGrantAuthProvider()
}
