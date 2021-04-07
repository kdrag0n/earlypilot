package dev.kdrag0n.patreondl.content

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*

fun Application.staticModule() {
    routing {
        // Redirect root to index of benefits on separate static site
        val benefitIndexUrl = environment.config.propertyOrNull("web.benefitIndexUrl")
        if (benefitIndexUrl != null) {
            get("/") {
                call.respondRedirect(benefitIndexUrl.getString())
            }
        }
    }
}