package dev.kdrag0n.patreondl

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.locations.*

fun Application.featuresModule() {
    // Typed routes
    install(Locations)

    if (environment.config.property("web.forwardedHeaders").getString().toBoolean()) {
        install(XForwardedHeaderSupport)
    }

    // In case users download with a parallel downloader, e.g. aria2
    install(PartialContent) {
        maxRangeCount = 10
    }

    // Support HEAD for completeness
    install(AutoHeadResponse)

    // Support CORS (e.g. for web installer downloads)
    install(CORS) {
        // Send cookies for session authentication
        allowCredentials = true

        val httpsOnly = environment.config.property("web.httpsOnly").getString().toBoolean()
        val schemes = if (httpsOnly) listOf("https") else listOf("https", "http")
        for (host in environment.config.property("web.corsAllowed").getList()) {
            host(host, schemes = schemes)
        }
    }

    // Request logging
    install(CallLogging)
}