package dev.kdrag0n.patreondl.content

import dev.kdrag0n.patreondl.config.Config
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import org.koin.ktor.ext.inject

fun Application.publicModule() {
    val config: Config by inject()

    routing {
        // Redirect root to index of benefits on separate static site
        get("/") {
            call.respondRedirect(config.content.benefitIndexUrl)
        }

        static("/static") {
            resources("static")
        }
    }
}