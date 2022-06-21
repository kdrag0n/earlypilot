package dev.kdrag0n.patreondl.content

import dev.kdrag0n.patreondl.config.Config
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
