package dev.kdrag0n.patreondl.http

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*

fun Application.featuresModule() {
    // Typed routes
    install(Locations)

    // Reverse proxy support (X-Forwarded-*)
    if (environment.config.property("web.forwardedHeaders").getString().toBoolean()) {
        install(XForwardedHeaderSupport) {
            // Any unused header is a security issue.
            // TODO: file Ktor issue to avoid using reflection
            javaClass.getDeclaredField("hostHeaders").let { field ->
                field.isAccessible = true
                field.set(this, arrayListOf(HttpHeaders.XForwardedHost))
            }
            javaClass.getDeclaredField("protoHeaders").let { field ->
                field.isAccessible = true
                field.set(this, arrayListOf(HttpHeaders.XForwardedProto))
            }
            javaClass.getDeclaredField("forHeaders").let { field ->
                field.isAccessible = true
                field.set(this, arrayListOf(HttpHeaders.XForwardedFor))
            }
            javaClass.getDeclaredField("httpsFlagHeaders").let { field ->
                field.isAccessible = true
                field.set(this, arrayListOf<String>())
            }
        }
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