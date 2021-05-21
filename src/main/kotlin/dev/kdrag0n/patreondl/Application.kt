package dev.kdrag0n.patreondl

import dev.kdrag0n.patreondl.content.exclusiveModule
import dev.kdrag0n.patreondl.content.publicModule
import dev.kdrag0n.patreondl.data.databaseModule
import dev.kdrag0n.patreondl.events.webhooksModule
import dev.kdrag0n.patreondl.http.PatreonApi
import dev.kdrag0n.patreondl.http.featuresModule
import dev.kdrag0n.patreondl.security.authModule
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

    // Database
    val dbAvailable = databaseModule()

    // Patreon OAuth and session authentication + validation
    val patreonApi = PatreonApi()
    authModule(patreonApi, dbAvailable)

    // Patreon API webhooks
    webhooksModule(patreonApi)

    // Exclusive content
    exclusiveModule(dbAvailable)

    // Simple Public/static routes
    publicModule()
}