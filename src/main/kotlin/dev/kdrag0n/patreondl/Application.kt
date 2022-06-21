package dev.kdrag0n.patreondl

import dev.kdrag0n.patreondl.content.exclusiveModule
import dev.kdrag0n.patreondl.content.publicModule
import dev.kdrag0n.patreondl.data.initDatabase
import dev.kdrag0n.patreondl.external.patreon.webhooksModule
import dev.kdrag0n.patreondl.payments.paymentsModule
import dev.kdrag0n.patreondl.security.authModule
import dev.kdrag0n.patreondl.telemetry.telemetryModule
import io.ktor.server.application.*
import io.ktor.server.locations.*
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

    // Database
    initDatabase()

    // Patreon OAuth and session authentication + validation
    authModule()

    // Patreon API webhooks
    webhooksModule()

    // Exclusive content
    exclusiveModule()

    // Payments
    paymentsModule()

    // Telemetry
    telemetryModule()

    // Simple Public/static routes
    publicModule()
}
