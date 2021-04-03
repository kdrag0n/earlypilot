package dev.kdrag0n.patreondl

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.pipeline.*

fun Application.errorsModule() {
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { genericStatusError(it) }
        status(HttpStatusCode.Unauthorized) { genericStatusError(it) }
        status(HttpStatusCode.BadRequest) { genericStatusError(it) }

        exception<Throwable> { cause ->
            genericStatusError(HttpStatusCode.InternalServerError)
            throw cause
        }
    }
}

private suspend fun PipelineContext<*, ApplicationCall>.genericStatusError(status: HttpStatusCode) {
    call.respondText("${status.value} ${status.description}")
}